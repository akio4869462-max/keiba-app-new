package org.example.keibaapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.jsoup.HttpStatusException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 前走の走路バイアス特徴(MODEL_REVISION.md §3.1/§6.2)を計算する。
//   p_lat_c  = 前走で外を回した度合い(その馬の前走lat - 前走レースの平均lat)
//   p_hw_lat = 前走の向かい風 = 前走日のバイアス(自レース除外) × (-p_lat_c)
// 前走日のバイアスは「前走と同じ日・同じ場・同じ芝ダの全レース」から推定する。
// Yahooのレース番号は YY+場+回+日+R(10桁)で、同じ開催日の全レースは先頭8桁が共通で
// 末尾2桁が01〜12のため、開催日の一覧ページを探さずに前走のURLだけから全レースの
// 結果ページURLを組み立てられる。
//
// 過去のレース結果は不変なので、レースごとの統計をDB(RaceBiasStat)に永続化して一度だけ取得する。
// 初回は1開催日あたり最大12ページを順に取得する(1ページごとに0.5秒待つ)ため時間がかかるが、
// 以降は未取得の開催日の分だけで済む。
@Service
public class TrackBiasService {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
    private static final Pattern RACE_ID_PATTERN = Pattern.compile("/race/(?:index|result|denma)/(\\d{10})");
    private static final String RESULT_URL = "https://sports.yahoo.co.jp/keiba/race/result/";
    private static final int RACES_PER_DAY = 12;
    // 取得失敗(通信障害・ブレーカー作動中等)したレースを再試行しない時間
    private static final long FAILURE_RETRY_MILLIS = 30 * 60 * 1000L;
    // 存在しないレースを表す印(404)。次回以降のアクセスを省くためDBにも残す
    private static final String ABSENT = "なし";

    // 計算結果。どちらも「平均が0付近」の生の値で、標準化と重み付けはPredictionService側で行う
    public record Features(double pHwLat, double pLatC) {
    }

    // 結果ページの取得。テストでは差し替える
    interface ResultFetcher {
        // ページが存在しなければHttpStatusException(404)を投げる
        ResultPageInfo fetch(String raceId) throws IOException, InterruptedException;
    }

    private final RaceBiasStatRepository repository;
    private final ResultFetcher fetcher;
    private final Clock clock;
    private Map<String, Double> priors = new HashMap<>();
    private double defaultPrior = 0;

    private final Map<String, RaceBiasStat> statCache = new ConcurrentHashMap<>();
    private final Map<String, Long> failedUntil = new ConcurrentHashMap<>();
    private final Object fetchLock = new Object();

    @Autowired
    public TrackBiasService(RaceBiasStatRepository repository) {
        this(repository, TrackBiasService::fetchFromYahoo, Clock.system(JST));
    }

    TrackBiasService(RaceBiasStatRepository repository, ResultFetcher fetcher, Clock clock) {
        this.repository = repository;
        this.fetcher = fetcher;
        this.clock = clock;
    }

    private static ResultPageInfo fetchFromYahoo(String raceId) throws IOException, InterruptedException {
        // 他の取得処理と同様、続けてアクセスしないよう間隔を空ける
        Thread.sleep(500);
        return WebScraper.parseResultPage(WebScraper.getHTML(RESULT_URL + raceId));
    }

    // 場×芝ダの恒常バイアス(前年平均)。同日のバイアスがまだ分からない/少ないときの縮小先
    @PostConstruct
    void loadPriors() {
        try {
            JsonNode node = new ObjectMapper().readTree(
                    new ClassPathResource("residuals/bias_priors.json").getInputStream());

            Map<String, Double> loaded = new HashMap<>();
            node.fields().forEachRemaining(e -> loaded.put(e.getKey(), e.getValue().asDouble()));

            setPriors(loaded);
            System.out.println("【走路バイアス】prior読み込み完了: " + loaded.size() + "件");
        } catch (IOException e) {
            System.out.println("走路バイアスのpriorの読み込みに失敗しました(0として動作を継続): " + e.getMessage());
        }
    }

    void setPriors(Map<String, Double> loaded) {
        this.priors = loaded;
        this.defaultPrior = loaded.getOrDefault("_default", 0.0);
    }

    double priorFor(String venue, String surface) {
        return priors.getOrDefault(venue + "|" + surface, defaultPrior);
    }

    // lastRaceはその馬の前走。allowFetch=falseならDB・メモリにあるものだけを使い、
    // 通信は行わない(開催日の統計が揃っていなければ特徴なし)。
    // 前走が障害戦・地方/海外・取得不能のときは空(スコアへの寄与0)
    public Optional<Features> featuresFor(PastRaceInfo lastRace, boolean allowFetch) {
        if (lastRace == null || lastRace.getRaceUrl() == null || lastRace.getUmaban() == null) {
            return Optional.empty();
        }

        String raceId = extractRaceId(lastRace.getRaceUrl());
        String umabanText = lastRace.getUmaban().trim();

        if (raceId == null || !umabanText.matches("\\d+")) {
            return Optional.empty();
        }

        RaceBiasStat target = getStat(raceId, allowFetch);

        if (target == null || !isFlatSurface(target.getSurface())) {
            return Optional.empty();
        }

        Double lat = target.getLatMeans().get(Integer.parseInt(umabanText));
        Double meanLat = target.getMeanLat();

        if (lat == null || meanLat == null) {
            return Optional.empty();
        }

        double latC = lat - meanLat;

        // 同じ日・同じ場・同じ芝ダの他のレース(自レースは除く)
        String dayPrefix = raceId.substring(0, 8);
        List<TrackBiasCalculator.RaceBias> others = new ArrayList<>();

        for (int number = 1; number <= RACES_PER_DAY; number++) {
            String otherId = dayPrefix + String.format("%02d", number);

            if (otherId.equals(raceId)) {
                continue;
            }

            RaceBiasStat other = getStat(otherId, allowFetch);

            if (other == null) {
                // 一部が欠けた状態で計算すると、取得できた順序次第で値が揺れてしまう
                return Optional.empty();
            }

            if (target.getSurface().equals(other.getSurface())) {
                others.add(other.toRaceBias());
            }
        }

        double dayBias = TrackBiasCalculator.dayBias(others, priorFor(target.getVenue(), target.getSurface()));

        return Optional.of(new Features(dayBias * -latC, latC));
    }

    static String extractRaceId(String url) {
        Matcher matcher = RACE_ID_PATTERN.matcher(url);

        if (!matcher.find()) {
            return null;
        }

        String id = matcher.group(1);
        int venueCode = Integer.parseInt(id.substring(2, 4));

        // JRAの開催場コードは01〜10。地方・海外のIDは対象外
        return venueCode >= 1 && venueCode <= 10 ? id : null;
    }

    private static boolean isFlatSurface(String surface) {
        return "芝".equals(surface) || "ダ".equals(surface);
    }

    // レース1件の統計。メモリ → DB → (allowFetchなら)Yahooの順に探す。見つからなければnull
    RaceBiasStat getStat(String raceId, boolean allowFetch) {
        RaceBiasStat cached = statCache.get(raceId);

        if (cached != null) {
            return cached;
        }

        Optional<RaceBiasStat> stored = repository.findById(raceId);

        if (stored.isPresent()) {
            statCache.put(raceId, stored.get());
            return stored.get();
        }

        if (!allowFetch || isRecentlyFailed(raceId)) {
            return null;
        }

        synchronized (fetchLock) {
            // 待っている間に別スレッドが取得済みかもしれない
            cached = statCache.get(raceId);

            if (cached != null) {
                return cached;
            }

            return fetchAndStore(raceId);
        }
    }

    private boolean isRecentlyFailed(String raceId) {
        Long until = failedUntil.get(raceId);
        return until != null && System.currentTimeMillis() < until;
    }

    private RaceBiasStat fetchAndStore(String raceId) {
        try {
            ResultPageInfo page = fetcher.fetch(raceId);

            if (page == null) {
                markFailed(raceId);
                return null;
            }

            TrackBiasCalculator.RaceBias bias = "障害".equals(page.surface())
                    ? new TrackBiasCalculator.RaceBias(null, null, null)
                    : TrackBiasCalculator.raceBias(page.rows(), page.latMeans());

            RaceBiasStat stat = new RaceBiasStat(
                    raceId, page.date(), page.venue(), page.surface(),
                    bias.bm(), bias.se(), bias.meanLat(), page.latMeans());

            // 当日以降のレースは結果が出揃っていない(古い結果欄が出ている)可能性があるため保存しない
            if (page.date().isBefore(LocalDate.now(clock))) {
                repository.save(stat);
                statCache.put(raceId, stat);
            }

            return stat;
        } catch (HttpStatusException e) {
            if (e.getStatusCode() == 404) {
                // その開催日にそのレース番号は無い。以後アクセスしないよう印を残す
                RaceBiasStat absent = new RaceBiasStat(
                        raceId, null, "", ABSENT, null, null, null, Map.of());
                repository.save(absent);
                statCache.put(raceId, absent);
                return absent;
            }

            markFailed(raceId);
            return null;
        } catch (IOException e) {
            markFailed(raceId);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private void markFailed(String raceId) {
        failedUntil.put(raceId, System.currentTimeMillis() + FAILURE_RETRY_MILLIS);
    }
}

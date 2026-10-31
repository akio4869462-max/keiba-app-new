package org.example.keibaapp;

import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// 開催日の朝に、当日の全レースの単勝オッズを1回ずつ記録する(MorningOdds)。
// 発走10分前のオッズとの差(オッズの動き)は、支持が増えた馬ほど買われすぎという弱いシグナルで
// (検証2023-26で +0.0012、MODEL_REVISION.md §8.12)、学習データの「朝」と同じ時間帯のオッズが要る。
// 馬の詳細ページは使わず、オッズページ(/race/odds/tfw/{id})を1レース1枚だけ取得するので軽い。
//
// 朝8時台〜11時台に10分おきに実行するが、全レースを記録し終えたらその日は何もしない。
// 再起動や取得失敗で朝を逃した分も後から補うが、minutesToPostを残してあるので
// 発走直前に取った分は解析側で朝から除ける
@Service
public class MorningOddsService {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    // 記録対象の1レース。oddsUrlは単勝・複勝オッズページ
    record RaceTarget(String venue, int raceNumber, LocalTime raceTime, String oddsUrl) {
    }

    // 単勝オッズ(馬番→オッズ)の取得。テストでは差し替える
    interface WinOddsFetcher {
        Map<Integer, Double> fetch(String oddsUrl) throws IOException, InterruptedException;
    }

    private final MorningOddsRepository repository;
    private final RaceParserService raceParserService;
    private final WinOddsFetcher fetcher;
    private final Clock clock;

    // その日の全レースを記録し終えた日(以後はスキップ)
    private volatile LocalDate completedDate;

    // 開催一覧から数えた、その日のレース数(記録の自己監視で「全レース記録できたか」の比較に使う)
    private volatile LocalDate targetDate;
    private volatile int targetCount;

    @Autowired
    public MorningOddsService(MorningOddsRepository repository, RaceParserService raceParserService) {
        this(repository, raceParserService, MorningOddsService::fetchFromYahoo, Clock.system(JST));
    }

    MorningOddsService(MorningOddsRepository repository, RaceParserService raceParserService,
                       WinOddsFetcher fetcher, Clock clock) {
        this.repository = repository;
        this.raceParserService = raceParserService;
        this.fetcher = fetcher;
        this.clock = clock;
    }

    private static Map<Integer, Double> fetchFromYahoo(String oddsUrl) throws IOException, InterruptedException {
        Thread.sleep(500);
        return WebScraper.parseWinPlaceOdds(WebScraper.getHTML(oddsUrl)).win();
    }

    // 今日の開催のレース数。まだ一覧を数えていない(再起動直後など)・開催が無い日は0
    public int getTodayRaceCount(LocalDate today) {
        return today.equals(targetDate) ? targetCount : 0;
    }

    // 曜日をハードコードせず毎日実行する(非開催日は一覧に今日の日付が無いので何も記録しない)
    @Scheduled(cron = "0 5/10 8-11 * * *", zone = "Asia/Tokyo")
    public void scheduledCapture() {
        LocalDateTime now = LocalDateTime.now(clock);

        if (now.toLocalDate().equals(completedDate)) {
            return;
        }

        try {
            List<RaceTarget> targets = collectTodayTargets(now.toLocalDate());
            targetDate = now.toLocalDate();
            targetCount = targets.size();
            int saved = capture(targets, now);
            System.out.println("【朝オッズ記録】" + targets.size() + "レース中 " + saved + "レースを新規に記録");
        } catch (Exception e) {
            System.out.println("【朝オッズ記録】失敗: " + e.getMessage());
        }
    }

    // トップページ→今日の日付の開催場一覧→各レースのオッズページURL。開催が無ければ空
    private List<RaceTarget> collectTodayTargets(LocalDate today) throws IOException {
        String todayText = today.format(DateTimeFormatter.ofPattern("yyyy年M月d日"));
        List<RaceTarget> targets = new ArrayList<>();

        Document top = WebScraper.getHTML("https://sports.yahoo.co.jp/keiba/");

        for (org.jsoup.nodes.Element link : top.select("a[href*=/keiba/race/list/]")) {
            Document listDoc = WebScraper.getHTML(link.attr("abs:href"));

            if (!listDoc.title().contains(todayText)) {
                continue;
            }

            String venue = raceParserService.extractVenueName(listDoc.title());

            for (RaceParserService.RaceSchedule schedule : raceParserService.getRaceSchedules(listDoc)) {
                targets.add(new RaceTarget(
                        venue,
                        raceParserService.getRaceNumber(schedule.raceUrl()),
                        schedule.raceTime(),
                        schedule.raceUrl().replace("/race/denma/", "/race/odds/tfw/")));
            }
        }

        return targets;
    }

    // 未記録のレースだけオッズを取得して保存する。戻り値は新規に記録したレース数。
    // 全レースが記録済みになったら、その日はもう実行しない
    int capture(List<RaceTarget> targets, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        int saved = 0;
        boolean allDone = !targets.isEmpty();

        for (RaceTarget target : targets) {
            if (repository.existsByRaceDateAndVenueAndRaceNumber(today, target.venue(), target.raceNumber())) {
                continue;
            }

            try {
                Map<Integer, Double> odds = fetcher.fetch(target.oddsUrl());

                if (odds.isEmpty()) {
                    allDone = false;
                    continue;
                }

                int minutesToPost = (int) Duration.between(now.toLocalTime(), target.raceTime()).toMinutes();
                List<MorningOdds> rows = new ArrayList<>();

                for (Map.Entry<Integer, Double> entry : odds.entrySet()) {
                    rows.add(new MorningOdds(today, target.venue(), target.raceNumber(),
                            entry.getKey(), entry.getValue(), minutesToPost, now));
                }

                repository.saveAll(rows);
                saved++;
            } catch (IOException e) {
                allDone = false;
                System.out.println("【朝オッズ記録】取得失敗: " + target.oddsUrl() + " / " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return saved;
            }
        }

        if (allDone) {
            completedDate = today;
        }

        return saved;
    }
}

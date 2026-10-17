package org.example.keibaapp;

import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class HorseEnrichmentService {

    private final PredictionService predictionService;
    private final RaceCacheService raceCacheService;
    private final AiPromptService aiPromptService;
    private final AiService aiService;
    private final TrackBiasService trackBiasService;
    private static final boolean DEBUG_AI_PROMPT = true;

    public HorseEnrichmentService(
            PredictionService predictionService,
            RaceCacheService raceCacheService,
            AiPromptService aiPromptService,
            AiService aiService,
            TrackBiasService trackBiasService) {

        this.trackBiasService = trackBiasService;
        this.predictionService = predictionService;
        this.raceCacheService = raceCacheService;
        this.aiPromptService = aiPromptService;
        this.aiService = aiService;
    }

    public HorseDetailInfo getHorseDetail(
            String horseUrl,
            boolean historical) throws InterruptedException {

        // historicalモードは結果確定チェックに使われ、土日は結果が出るまで
        // 何度もポーリングされる。キャッシュしてしまうと未確定だった時点の
        // データが固定され、後で結果が出ても永遠に反映されなくなるため、
        // このモードだけは毎回取得し直す
        if (historical) {
            Thread.sleep(500);
            return WebScraper.getHistoricalHorseDetailInfo(horseUrl);
        }

        String cacheKey = "today:" + horseUrl;

        HorseDetailInfo detail =
                raceCacheService.getHorseDetail(cacheKey);

        if (detail == null) {
            Thread.sleep(500);

            detail = WebScraper.getTodayHorseDetailInfo(horseUrl);

            raceCacheService.putHorseDetail(cacheKey, detail);
        }

        return detail;
    }

    public void fetchHorseDetail(Horse horse, boolean isHistorical) throws InterruptedException {
        HorseDetailInfo detail = getHorseDetail(horse.getHorseUrl(), isHistorical);

        horse.setLastRace(detail.getLastRace());
        horse.setSecondLastRace(detail.getSecondLastRace());
        horse.setThirdLastRace(detail.getThirdLastRace());
        horse.setBreeder(detail.getBreeder());

        if (isHistorical) {
            horse.setActualRace(detail.getActualRace());
        }
    }

    // 前走の走路バイアス特徴(p_hw_lat / p_lat_c)を付与する。結果確定後に繰り返し呼ばれる
    // historicalモードでは通信せず、取得済みの統計だけを使う(特徴が無ければ寄与0)
    public void enrichTrackBias(Horse horse, boolean isHistorical) {
        trackBiasService.featuresFor(horse.getLastRace(), !isHistorical).ifPresent(features -> {
            horse.setPHwLat(features.pHwLat());
            horse.setPLatC(features.pLatC());
        });
    }

    // 発走10分前の再取得時に、単勝・複勝オッズページから複勝ギャップを付与する。
    // 取得に失敗してもギャップなし(寄与0)で続行する
    // 発走10分前の板(複勝・馬連・ワイド・馬単)の取得で最後に起きた問題。本番のログを直接見られないため
    // /statusに表示して、取得失敗か解析結果が空かを切り分けられるようにする
    private volatile String lastBoardIssue = "なし";

    public String getLastBoardIssue() {
        return lastBoardIssue;
    }

    private void noteBoardIssue(String message) {
        lastBoardIssue = java.time.LocalTime.now(java.time.ZoneId.of("Asia/Tokyo"))
                .truncatedTo(java.time.temporal.ChronoUnit.SECONDS) + " " + message;
        System.out.println("板の取得: " + message);
    }

    public void enrichFukuGap(List<Horse> horses, String raceUrl) throws InterruptedException {
        Thread.sleep(500);

        try {
            WebScraper.WinPlaceOdds odds = WebScraper.parseWinPlaceOdds(
                    WebScraper.getHTML(raceUrl.replace("/race/denma/", "/race/odds/tfw/")));

            java.util.Map<Integer, Double> gaps = FukuGapCalculator.compute(odds.win(), odds.placeMid());

            if (gaps.isEmpty()) {
                noteBoardIssue("複勝ギャップなし(単勝" + odds.win().size() + "頭/複勝" + odds.placeMid().size() + "頭を解析): " + raceUrl);
            }

            for (Horse horse : horses) {
                if (horse.getUmaban() != null && horse.getUmaban().trim().matches("\\d+")) {
                    horse.setFukuGap(gaps.get(Integer.parseInt(horse.getUmaban().trim())));
                }
            }
        } catch (java.io.IOException e) {
            noteBoardIssue("複勝ギャップの取得に失敗: " + raceUrl + " / " + e.getMessage());
        }
    }

    // 発走10分前の再取得時に、馬連・ワイド・馬単の板から馬ごとのシェアを付ける(前向き記録用で、
    // モデルには使わない)。プールごとに独立して取得し、失敗したプールはnullのまま続行する
    public void enrichPoolShares(List<Horse> horses, String raceUrl) throws InterruptedException {
        java.util.Map<Integer, Double> umaren = fetchShares(raceUrl, "ur", true);
        java.util.Map<Integer, Double> wide = fetchShares(raceUrl, "wide", true);
        java.util.Map<Integer, Double> exacta = fetchShares(raceUrl, "ut", false);

        for (Horse horse : horses) {
            try {
                int umaban = Integer.parseInt(horse.getUmaban().trim());
                horse.setUmarenShare(umaren.get(umaban));
                horse.setWideShare(wide.get(umaban));
                horse.setExactaFirstShare(exacta.get(umaban));
            } catch (NumberFormatException | NullPointerException e) {
                // 馬番が読めない馬はシェアなし
            }
        }
    }

    private java.util.Map<Integer, Double> fetchShares(String raceUrl, String pool, boolean symmetric)
            throws InterruptedException {
        Thread.sleep(500);

        try {
            List<WebScraper.PairOdds> pairs = WebScraper.parsePairOdds(
                    WebScraper.getHTML(raceUrl.replace("/race/denma/", "/race/odds/" + pool + "/")));

            if (pairs.isEmpty()) {
                noteBoardIssue("板(" + pool + ")の解析結果が空: " + raceUrl);
            }

            return symmetric ? PoolShareCalculator.symmetricShares(pairs) : PoolShareCalculator.firstShares(pairs);
        } catch (java.io.IOException e) {
            noteBoardIssue("板(" + pool + ")の取得に失敗: " + raceUrl + " / " + e.getMessage());
            return java.util.Map.of();
        }
    }

    public void enrichJockeyStats(Horse horse) throws InterruptedException {
        String jockeyUrl = horse.getJockeyUrl();

        if (jockeyUrl == null || jockeyUrl.isBlank()) {
            horse.setJockeyStats(JockeyStats.empty());
            return;
        }

        JockeyStats stats = raceCacheService.getJockeyStats(jockeyUrl);

        if (stats == null) {
            Thread.sleep(500);

            stats = WebScraper.getJockeyStats(jockeyUrl);

            raceCacheService.putJockeyStats(jockeyUrl, stats);
        }

        horse.setJockeyStats(stats);
    }

    // レース内の全馬について予想モデル(市場確率・勝率・妙味・人気)をまとめて算出する。
    // pはレース単位のsoftmaxで決まるため、1頭ずつではなくレース単位で呼ぶ
    public void applyRaceModel(List<Horse> horses) {
        predictionService.applyRaceModel(horses);
    }

    public void enrichAiPrompt(RaceInfo race) {
        for (Horse horse : race.getHorses()) {
            horse.setAiPrompt(
                    aiPromptService.createPrompt(race, horse)
            );

            horse.setAiComment(
                    aiService.createComment(horse)
            );

            if (DEBUG_AI_PROMPT) {
                System.out.println("========== AI PROMPT ==========");
                System.out.println(horse.getAiPrompt());
                System.out.println("===============================");
            }
        }
    }
}
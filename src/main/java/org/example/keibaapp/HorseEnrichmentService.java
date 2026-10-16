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
    public void enrichFukuGap(List<Horse> horses, String raceUrl) throws InterruptedException {
        Thread.sleep(500);

        try {
            WebScraper.WinPlaceOdds odds = WebScraper.parseWinPlaceOdds(
                    WebScraper.getHTML(raceUrl.replace("/race/denma/", "/race/odds/tfw/")));

            java.util.Map<Integer, Double> gaps = FukuGapCalculator.compute(odds.win(), odds.placeMid());

            for (Horse horse : horses) {
                if (horse.getUmaban() != null && horse.getUmaban().trim().matches("\\d+")) {
                    horse.setFukuGap(gaps.get(Integer.parseInt(horse.getUmaban().trim())));
                }
            }
        } catch (java.io.IOException e) {
            System.out.println("複勝ギャップの取得に失敗(寄与0で続行): " + raceUrl + " / " + e.getMessage());
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
package org.example.keibaapp;

import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class HorseEnrichmentService {

    private final PredictionService predictionService;
    private final RaceCacheService raceCacheService;
    private final AiPromptService aiPromptService;
    private final AiService aiService;
    private static final boolean DEBUG_AI_PROMPT = true;

    public HorseEnrichmentService(
            PredictionService predictionService,
            RaceCacheService raceCacheService,
            AiPromptService aiPromptService,
            AiService aiService) {

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
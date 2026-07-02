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

        String cacheKey = historical
                ? "historical:" + horseUrl
                : "today:" + horseUrl;

        HorseDetailInfo detail =
                raceCacheService.getHorseDetail(cacheKey);

        if (detail == null) {
            Thread.sleep(500);

            detail = historical
                    ? WebScraper.getHistoricalHorseDetailInfo(horseUrl)
                    : WebScraper.getTodayHorseDetailInfo(horseUrl);

            raceCacheService.putHorseDetail(cacheKey, detail);
        }

        return detail;
    }

    private void applyRaceDetail(
            Horse horse,          // setする対象
            HorseDetailInfo detail,  // setする内容の元データ
            String currentCourse,    // スコア計算に必要
            String currentDistance   // スコア計算に必要
    ) {
        horse.setLastRace(detail.getLastRace());
        horse.setSecondLastRace(detail.getSecondLastRace());
        horse.setThirdLastRace(detail.getThirdLastRace());
        horse.setPredictionScore(
                predictionService.calculateScore(horse, currentCourse, currentDistance));
        horse.setPredictionReason(
                predictionService.createReason(horse, currentCourse, currentDistance));
    }

    public void fetchHorseDetail(Horse horse, boolean isHistorical) throws InterruptedException {
        HorseDetailInfo detail = getHorseDetail(horse.getHorseUrl(), isHistorical);

        horse.setLastRace(detail.getLastRace());
        horse.setSecondLastRace(detail.getSecondLastRace());
        horse.setThirdLastRace(detail.getThirdLastRace());
    }

    public void applyScore(
            Horse horse,
            List<Horse> allHorses,
            String currentCourse,
            String currentDistance) {
        horse.setPredictionScore(
                predictionService.calculateExpectedValue(horse, allHorses));
        horse.setPredictionReason(
                predictionService.createReason(horse, currentCourse, currentDistance));
    }

    public void enrichTodayHorse(
            Horse horse,
            String currentCourse,
            String currentDistance) throws InterruptedException {
        HorseDetailInfo detail =
                getHorseDetail(horse.getHorseUrl(), false);

        applyRaceDetail(horse, detail, currentCourse, currentDistance);

//        horse.setAiPrompt(
//                aiPromptService.createPrompt(
//                        race,
//                        horse));
    }

    public void enrichHistoricalHorse(
            Horse horse,
            String currentCourse,
            String currentDistance) throws InterruptedException {
        HorseDetailInfo detail =
                getHorseDetail(horse.getHorseUrl(), true);

        horse.setActualRace(detail.getActualRace());
        applyRaceDetail(horse, detail,currentCourse, currentDistance);

//        horse.setAiPrompt(
//                aiPromptService.createPrompt(
//                        race,
//                        horse));
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
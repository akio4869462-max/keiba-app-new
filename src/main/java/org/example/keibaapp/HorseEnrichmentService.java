package org.example.keibaapp;

import org.springframework.stereotype.Service;

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

    public void enrichTodayHorse(
            Horse horse,
            String currentCourse,
            String currentDistance) throws InterruptedException {
        HorseDetailInfo detail =
                getHorseDetail(horse.getHorseUrl(), false);

        horse.setLastRace(detail.getLastRace());
        horse.setSecondLastRace(detail.getSecondLastRace());
        horse.setThirdLastRace(detail.getThirdLastRace());

        horse.setPredictionScore(
                predictionService.calculateScore(horse, currentCourse, currentDistance));

        horse.setPredictionReason(
                predictionService.createReason(horse, currentCourse, currentDistance));

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

        horse.setLastRace(detail.getLastRace());
        horse.setSecondLastRace(detail.getSecondLastRace());
        horse.setThirdLastRace(detail.getThirdLastRace());
        horse.setActualRace(detail.getActualRace());

        horse.setPredictionScore(
                predictionService.calculateScore(horse, currentCourse, currentDistance));

        horse.setPredictionReason(
                predictionService.createReason(horse, currentCourse, currentDistance));

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
            if (DEBUG_AI_PROMPT) {
                System.out.println("========== AI PROMPT ==========");
                System.out.println(horse.getAiPrompt());
                System.out.println("===============================");
            }
        }
    }
}
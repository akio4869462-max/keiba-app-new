package org.example.keibaapp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HorseEnrichmentServiceTest {

    @Test
    void enrichTodayHorse_shouldSetPastRacesAndPrediction() throws InterruptedException {
        PredictionService predictionService = new PredictionService();
        RaceCacheService raceCacheService = new RaceCacheService();
        AiPromptService aiPromptService = new AiPromptService();
        AiService aiService = new AiService();

        HorseEnrichmentService service =
                new HorseEnrichmentService(
                        predictionService,
                        raceCacheService,
                        aiPromptService,
                        aiService
                );

        Horse horse = new Horse("1", "1", "テストホース", "テスト騎手", "57.0", 5.0);
        horse.setHorseUrl("https://example.com/horse/1");

        HorseDetailInfo detail = new HorseDetailInfo(
                new PastRaceInfo("前走", 1, "GI", 1),
                new PastRaceInfo("2走前", 2, "GII", 2),
                new PastRaceInfo("3走前", 3, "GIII", 3),
                PastRaceInfo.empty()
        );

        raceCacheService.putHorseDetail(
                "today:" + horse.getHorseUrl(),
                detail
        );

        service.enrichTodayHorse(horse, "芝", "2000m");

        assertNotNull(horse.getLastRace());
        assertNotNull(horse.getSecondLastRace());
        assertNotNull(horse.getThirdLastRace());
        assertTrue(horse.getPredictionScore() > 0);
        assertTrue(horse.getPredictionReason().contains("前走"));
    }

    @Test
    void enrichHistoricalHorse_shouldSetActualRace() throws InterruptedException {
        PredictionService predictionService = new PredictionService();
        RaceCacheService raceCacheService = new RaceCacheService();
        AiPromptService aiPromptService = new AiPromptService();
        AiService aiService = new AiService();

        HorseEnrichmentService service =
                new HorseEnrichmentService(
                        predictionService,
                        raceCacheService,
                        aiPromptService,
                        aiService
                );

        Horse horse = new Horse("1", "1", "テストホース", "テスト騎手", "57.0", 5.0);
        horse.setHorseUrl("https://example.com/horse/2");

        HorseDetailInfo detail = new HorseDetailInfo(
                new PastRaceInfo("前走", 1, "GI", 1),
                new PastRaceInfo("2走前", 2, "GII", 2),
                new PastRaceInfo("3走前", 3, "GIII", 3),
                new PastRaceInfo("実際のレース", 4, "GI", 2)
        );

        raceCacheService.putHorseDetail(
                "historical:" + horse.getHorseUrl(),
                detail
        );

        service.enrichHistoricalHorse(horse, "芝", "2000m");

        assertNotNull(horse.getActualRace());
        assertEquals(4, horse.getActualRace().getRank());
        assertTrue(horse.getPredictionScore() > 0);
    }
}
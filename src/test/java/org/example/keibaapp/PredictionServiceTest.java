package org.example.keibaapp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PredictionServiceTest {

    private final PredictionService predictionService =
            new PredictionService();

    @Test
    void calculateScore_shouldReturnHighScoreForStrongHorse() {
        Horse horse = new Horse(
                "1",
                "1",
                "テストホース",
                "テスト騎手",
                "57.0",
                5.0
        );

        horse.setLastRace(new PastRaceInfo("前走", 1, "GI", 1));
        horse.setSecondLastRace(new PastRaceInfo("2走前", 2, "GII", 2));
        horse.setThirdLastRace(new PastRaceInfo("3走前", 3, "GIII", 3));

        double score = predictionService.calculateScore(horse);

        assertTrue(score > 0);
    }

    @Test
    void calculateScore_shouldIgnoreInvalidOdds() {
        Horse horse = new Horse(
                "1",
                "1",
                "テストホース",
                "テスト騎手",
                "57.0",
                999.9
        );

        horse.setLastRace(PastRaceInfo.empty());
        horse.setSecondLastRace(PastRaceInfo.empty());
        horse.setThirdLastRace(PastRaceInfo.empty());

        double score = predictionService.calculateScore(horse);

        assertEquals(0, score);
    }

    @Test
    void createReason_shouldContainRaceInformation() {
        Horse horse = new Horse(
                "1",
                "1",
                "テストホース",
                "テスト騎手",
                "57.0",
                10.0
        );

        horse.setLastRace(new PastRaceInfo("前走", 1, "GI", 1));
        horse.setSecondLastRace(PastRaceInfo.empty());
        horse.setThirdLastRace(PastRaceInfo.empty());

        String reason = predictionService.createReason(horse, "2000m");

        assertTrue(reason.contains("オッズ評価"));
        assertTrue(reason.contains("前走"));
        assertTrue(reason.contains("GI"));
        assertTrue(reason.contains("1着"));
    }
}
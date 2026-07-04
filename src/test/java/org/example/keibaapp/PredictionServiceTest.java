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

        String reason = predictionService.createReason(horse, "芝", "2000m");

        assertTrue(reason.contains("オッズ評価"));
        assertTrue(reason.contains("前走"));
        assertTrue(reason.contains("GI"));
        assertTrue(reason.contains("1着"));
    }

    @Test
    void calculateScore_shouldAddBonusForInnerWakuOnTurfSprint() {
        Horse innerHorse = new Horse(
                "1",
                "1",
                "テストホース",
                "テスト騎手",
                "57.0",
                5.0
        );

        Horse outerHorse = new Horse(
                "8",
                "8",
                "テストホース2",
                "テスト騎手2",
                "57.0",
                5.0
        );

        innerHorse.setLastRace(PastRaceInfo.empty());
        innerHorse.setSecondLastRace(PastRaceInfo.empty());
        innerHorse.setThirdLastRace(PastRaceInfo.empty());

        outerHorse.setLastRace(PastRaceInfo.empty());
        outerHorse.setSecondLastRace(PastRaceInfo.empty());
        outerHorse.setThirdLastRace(PastRaceInfo.empty());

        double innerScore = predictionService.calculateScore(innerHorse, "芝", "1200m");
        double outerScore = predictionService.calculateScore(outerHorse, "芝", "1200m");

        assertTrue(innerScore > outerScore);
    }

    @Test
    void calculateScore_shouldIgnoreWakuOnLongDistanceRace() {
        Horse innerHorse = new Horse(
                "1",
                "1",
                "テストホース",
                "テスト騎手",
                "57.0",
                5.0
        );

        Horse outerHorse = new Horse(
                "8",
                "8",
                "テストホース2",
                "テスト騎手2",
                "57.0",
                5.0
        );

        innerHorse.setLastRace(PastRaceInfo.empty());
        innerHorse.setSecondLastRace(PastRaceInfo.empty());
        innerHorse.setThirdLastRace(PastRaceInfo.empty());

        outerHorse.setLastRace(PastRaceInfo.empty());
        outerHorse.setSecondLastRace(PastRaceInfo.empty());
        outerHorse.setThirdLastRace(PastRaceInfo.empty());

        double innerScore = predictionService.calculateScore(innerHorse, "芝", "2000m");
        double outerScore = predictionService.calculateScore(outerHorse, "芝", "2000m");

        assertEquals(innerScore, outerScore);
    }

    @Test
    void calculateScore_shouldAddBonusForHigherWinRateJockey() {
        Horse strongJockeyHorse = new Horse(
                "1",
                "1",
                "テストホース",
                "テスト騎手",
                "57.0",
                5.0
        );

        Horse weakJockeyHorse = new Horse(
                "2",
                "2",
                "テストホース2",
                "テスト騎手2",
                "57.0",
                5.0
        );

        strongJockeyHorse.setLastRace(PastRaceInfo.empty());
        strongJockeyHorse.setSecondLastRace(PastRaceInfo.empty());
        strongJockeyHorse.setThirdLastRace(PastRaceInfo.empty());
        strongJockeyHorse.setJockeyStats(new JockeyStats(0.2, 0.4));

        weakJockeyHorse.setLastRace(PastRaceInfo.empty());
        weakJockeyHorse.setSecondLastRace(PastRaceInfo.empty());
        weakJockeyHorse.setThirdLastRace(PastRaceInfo.empty());
        weakJockeyHorse.setJockeyStats(new JockeyStats(0.05, 0.1));

        double strongScore = predictionService.calculateScore(strongJockeyHorse, "ダ", "2000m");
        double weakScore = predictionService.calculateScore(weakJockeyHorse, "ダ", "2000m");

        assertTrue(strongScore > weakScore);
    }

    @Test
    void calculateScore_shouldTreatMissingJockeyStatsAsZero() {
        Horse horse = new Horse(
                "1",
                "1",
                "テストホース",
                "テスト騎手",
                "57.0",
                5.0
        );

        horse.setLastRace(PastRaceInfo.empty());
        horse.setSecondLastRace(PastRaceInfo.empty());
        horse.setThirdLastRace(PastRaceInfo.empty());

        double score = predictionService.calculateScore(horse, "ダ", "2000m");

        assertEquals(0, score);
    }
}
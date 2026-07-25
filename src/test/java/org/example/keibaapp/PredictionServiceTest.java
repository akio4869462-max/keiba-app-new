package org.example.keibaapp;

import org.junit.jupiter.api.Test;

import java.util.List;

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

    @Test
    void calculateExpectedValue_shouldSumToApproximately100AcrossField() {
        Horse horseA = new Horse("1", "1", "馬A", "騎手A", "57.0", 5.0);
        Horse horseB = new Horse("2", "2", "馬B", "騎手B", "57.0", 10.0);

        for (Horse horse : List.of(horseA, horseB)) {
            horse.setLastRace(new PastRaceInfo("前走", 1, "GI", 1));
            horse.setSecondLastRace(PastRaceInfo.empty());
            horse.setThirdLastRace(PastRaceInfo.empty());
        }

        List<Horse> field = List.of(horseA, horseB);

        double totalExpectedValue = predictionService.calculateExpectedValue(horseA, field, "芝", "2000m")
                + predictionService.calculateExpectedValue(horseB, field, "芝", "2000m");

        assertEquals(100.0, totalExpectedValue, 0.01);
    }

    @Test
    void calculateExpectedValue_shouldRewardCourseAptitude() {
        // 過去走・オッズは全く同じだが、現在のコースへの適性だけが違う2頭を比較する。
        // calculateExpectedValueがcalculateScore(horse)(前走のみ)しか見ていなかった旧実装では
        // この差は反映されなかったが、修正後は距離・コース適性が反映されるはず
        Horse turfSpecialist = new Horse("1", "1", "芝実績馬", "騎手A", "57.0", 5.0);
        Horse dirtSpecialist = new Horse("2", "2", "ダート実績馬", "騎手B", "57.0", 5.0);

        PastRaceInfo turfWin = new PastRaceInfo("前走", 1, "GI", 1);
        turfWin.setCourse("芝");
        turfWin.setDistance("2000m");
        turfSpecialist.setLastRace(turfWin);
        turfSpecialist.setSecondLastRace(PastRaceInfo.empty());
        turfSpecialist.setThirdLastRace(PastRaceInfo.empty());

        PastRaceInfo dirtWin = new PastRaceInfo("前走", 1, "GI", 1);
        dirtWin.setCourse("ダ");
        dirtWin.setDistance("2000m");
        dirtSpecialist.setLastRace(dirtWin);
        dirtSpecialist.setSecondLastRace(PastRaceInfo.empty());
        dirtSpecialist.setThirdLastRace(PastRaceInfo.empty());

        List<Horse> field = List.of(turfSpecialist, dirtSpecialist);

        double turfRaceScore = predictionService.calculateExpectedValue(turfSpecialist, field, "芝", "2000m");
        double dirtRaceScore = predictionService.calculateExpectedValue(dirtSpecialist, field, "芝", "2000m");

        assertTrue(turfRaceScore > dirtRaceScore,
                "芝実績馬の方が芝レースでのスコアが高くなるはず: turf=" + turfRaceScore + " dirt=" + dirtRaceScore);
    }

    @Test
    void calculateExpectedValue_shouldReturnZeroForInvalidOdds() {
        Horse invalidOddsHorse = new Horse("1", "1", "馬A", "騎手A", "57.0", 999.9);
        Horse normalHorse = new Horse("2", "2", "馬B", "騎手B", "57.0", 5.0);

        for (Horse horse : List.of(invalidOddsHorse, normalHorse)) {
            horse.setLastRace(new PastRaceInfo("前走", 1, "GI", 1));
            horse.setSecondLastRace(PastRaceInfo.empty());
            horse.setThirdLastRace(PastRaceInfo.empty());
        }

        List<Horse> field = List.of(invalidOddsHorse, normalHorse);

        double score = predictionService.calculateExpectedValue(invalidOddsHorse, field, "芝", "2000m");

        assertEquals(0, score);
    }
}
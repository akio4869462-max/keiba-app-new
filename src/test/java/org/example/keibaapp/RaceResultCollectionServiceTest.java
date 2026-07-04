package org.example.keibaapp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RaceResultCollectionServiceTest {

    private Horse horseWithActualRace(String actualCourse, String actualDistance, int rank) {
        Horse horse = new Horse("1", "1", "テスト馬", "テスト騎手", "57.0", 5.0);
        horse.setActualRace(new PastRaceInfo("直近走", rank, "OP", 1));
        horse.getActualRace().setCourse(actualCourse);
        horse.getActualRace().setDistance(actualDistance);
        return horse;
    }

    @Test
    void isConfirmedForThisRace_shouldReturnTrueWhenCourseAndDistanceMatch() {
        Horse horse = horseWithActualRace("芝", "2000m", 3);

        assertTrue(RaceResultCollectionService.isConfirmedForThisRace(horse, "芝", "2000m"));
    }

    @Test
    void isConfirmedForThisRace_shouldReturnFalseWhenDistanceDiffers() {
        // まだこのレースが行われておらず、馬の個人ページの最新走が
        // 別の距離の過去レースを指しているケースを想定
        Horse horse = horseWithActualRace("芝", "1600m", 1);

        assertFalse(RaceResultCollectionService.isConfirmedForThisRace(horse, "芝", "2000m"));
    }

    @Test
    void isConfirmedForThisRace_shouldTreatDaAndDaatoAsSameCourseType() {
        // WebScraper.getRaceCourseは「ダート」、過去走のextractCourseは「ダ」を返すため
        // 表記ゆれを吸収できているか確認
        Horse horse = horseWithActualRace("ダ", "1200m", 2);

        assertTrue(RaceResultCollectionService.isConfirmedForThisRace(horse, "ダート", "1200m"));
    }

    @Test
    void isConfirmedForThisRace_shouldFallBackToDistanceOnlyWhenPastCourseIsBlank() {
        // 障害レースはextractCourseが「障」を認識できず空文字になるため、
        // コース種別チェックをスキップして距離一致のみで確定扱いにする
        Horse horse = horseWithActualRace("", "3000m", 5);

        assertTrue(RaceResultCollectionService.isConfirmedForThisRace(horse, "芝", "3000m"));
    }

    @Test
    void isConfirmedForThisRace_shouldReturnFalseWhenRankIsZero() {
        Horse horse = horseWithActualRace("芝", "2000m", 0);

        assertFalse(RaceResultCollectionService.isConfirmedForThisRace(horse, "芝", "2000m"));
    }

    @Test
    void isConfirmedForThisRace_shouldReturnFalseWhenActualRaceIsNull() {
        Horse horse = new Horse("1", "1", "テスト馬", "テスト騎手", "57.0", 5.0);

        assertFalse(RaceResultCollectionService.isConfirmedForThisRace(horse, "芝", "2000m"));
    }
}

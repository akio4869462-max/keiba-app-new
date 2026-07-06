package org.example.keibaapp;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RaceResultStatsServiceTest {

    private final RaceResultStatsService statsService = new RaceResultStatsService();

    private RaceResultRecord record(
            LocalDate date, double score, double odds, int predictionRank, int actualRank) {

        return new RaceResultRecord(
                date, "テスト場", 1, "テストレース", "テスト馬",
                odds, predictionRank, score, actualRank);
    }

    private RaceResultRecord raceRecord(
            LocalDate date, String venue, int raceNumber, String raceName, String horseName,
            int predictionRank, int actualRank) {

        return new RaceResultRecord(
                date, venue, raceNumber, raceName, horseName,
                5.0, predictionRank, 50, actualRank);
    }

    @Test
    void calculateRoi_shouldReturnOverHundredWhenWinPaysMoreThanStakes() {
        List<RaceResultRecord> topPicks = List.of(
                record(LocalDate.of(2026, 7, 4), 50, 3.0, 1, 1),
                record(LocalDate.of(2026, 7, 5), 50, 5.0, 1, 2)
        );

        double roi = statsService.calculateRoi(topPicks);

        // 100%が収支トントン。3.0(的中)+0(不的中)=3.0を2レース分(投資2)で回収 -> 150%
        assertEquals(150.0, roi, 0.001);
    }

    @Test
    void calculateRoi_shouldReturnZeroWhenNoBets() {
        double roi = statsService.calculateRoi(List.of());

        assertEquals(0, roi);
    }

    @Test
    void buildScoreBandStats_shouldSplitIntoFourEvenQuartilesByScoreDescending() {
        List<RaceResultRecord> records = List.of(
                record(LocalDate.of(2026, 7, 4), 90, 5.0, 1, 1),
                record(LocalDate.of(2026, 7, 4), 80, 5.0, 2, 5),
                record(LocalDate.of(2026, 7, 4), 70, 5.0, 3, 2),
                record(LocalDate.of(2026, 7, 4), 60, 5.0, 4, 8),
                record(LocalDate.of(2026, 7, 4), 50, 5.0, 5, 3),
                record(LocalDate.of(2026, 7, 4), 40, 5.0, 6, 9),
                record(LocalDate.of(2026, 7, 4), 30, 5.0, 7, 6),
                record(LocalDate.of(2026, 7, 4), 20, 5.0, 8, 10)
        );

        List<RaceResultStatsService.ScoreBandStat> bands = statsService.buildScoreBandStats(records);

        assertEquals(4, bands.size());
        assertEquals(2, bands.get(0).getTotal());
        assertEquals(2, bands.get(3).getTotal());
    }

    @Test
    void buildWeeklyStats_shouldOrderNewestWeekFirstAcrossYearBoundary() {
        List<RaceResultRecord> topPicks = List.of(
                record(LocalDate.of(2025, 12, 28), 50, 5.0, 1, 2),
                record(LocalDate.of(2026, 1, 4), 50, 5.0, 1, 1)
        );

        List<RaceResultStatsService.WeeklyStat> weeks = statsService.buildWeeklyStats(topPicks);

        assertEquals(2, weeks.size());
        assertEquals(100.0, weeks.get(0).getWinRate(), 0.001);
    }

    @Test
    void oddsBandLabel_shouldBucketCorrectly() {
        assertEquals("人気馬(5倍未満)", statsService.oddsBandLabel(4.9));
        assertEquals("中穴(5〜20倍)", statsService.oddsBandLabel(10.0));
        assertEquals("大穴(20倍超)", statsService.oddsBandLabel(25.0));
    }

    @Test
    void buildRaceGroups_shouldGroupByRaceAndSortHorsesByActualRank() {
        LocalDate date = LocalDate.of(2026, 7, 4);

        List<RaceResultRecord> records = List.of(
                raceRecord(date, "函館", 9, "レースA", "3着馬", 1, 3),
                raceRecord(date, "函館", 9, "レースA", "1着馬", 2, 1),
                raceRecord(date, "函館", 9, "レースA", "2着馬", 3, 2),
                raceRecord(date, "東京", 1, "レースB", "単独馬", 1, 1)
        );

        List<RaceResultGroup> groups = statsService.buildRaceGroups(records, List.of());

        assertEquals(2, groups.size());

        RaceResultGroup hakodateRace = groups.stream()
                .filter(g -> g.getVenue().equals("函館"))
                .findFirst()
                .orElseThrow();

        assertEquals(3, hakodateRace.getHorses().size());
        assertEquals("1着馬", hakodateRace.getHorses().get(0).getHorseName());
        assertEquals("2着馬", hakodateRace.getHorses().get(1).getHorseName());
        assertEquals("3着馬", hakodateRace.getHorses().get(2).getHorseName());
    }

    @Test
    void buildRaceGroups_shouldOrderMostRecentRaceDateFirst() {
        List<RaceResultRecord> records = List.of(
                raceRecord(LocalDate.of(2026, 6, 21), "函館", 9, "古いレース", "馬1", 1, 1),
                raceRecord(LocalDate.of(2026, 7, 4), "東京", 1, "新しいレース", "馬2", 1, 1)
        );

        List<RaceResultGroup> groups = statsService.buildRaceGroups(records, List.of());

        assertEquals("新しいレース", groups.get(0).getRaceName());
        assertEquals("古いレース", groups.get(1).getRaceName());
    }
}

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

    @Test
    void calculateRoi_shouldReturnPositiveWhenWinPaysMoreThanStakes() {
        List<RaceResultRecord> topPicks = List.of(
                record(LocalDate.of(2026, 7, 4), 50, 3.0, 1, 1),
                record(LocalDate.of(2026, 7, 5), 50, 5.0, 1, 2)
        );

        double roi = statsService.calculateRoi(topPicks);

        assertEquals(50.0, roi, 0.001);
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
        assertEquals("人気馬(3倍未満)", statsService.oddsBandLabel(2.9));
        assertEquals("中穴(3〜10倍)", statsService.oddsBandLabel(5.0));
        assertEquals("大穴(10倍超)", statsService.oddsBandLabel(15.0));
    }
}

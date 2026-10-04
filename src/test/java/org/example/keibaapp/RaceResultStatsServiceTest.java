package org.example.keibaapp;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RaceResultStatsServiceTest {

    private final RaceResultStatsService statsService = new RaceResultStatsService();

    private RaceResultRecord record(
            LocalDate date, double score, double odds, int predictionRank, int actualRank) {

        return new RaceResultRecord(
                date, "テスト場", 1, "テストレース", "テスト馬",
                odds, predictionRank, score, actualRank, 0, predictionRank, "test", null);
    }

    private RaceResultRecord raceRecord(
            LocalDate date, String venue, int raceNumber, String raceName, String horseName,
            int predictionRank, int actualRank) {

        return new RaceResultRecord(
                date, venue, raceNumber, raceName, horseName,
                5.0, predictionRank, 50, actualRank, 0, predictionRank, "test", null);
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

    @Test
    void groupRacesByDateAndVenue_shouldNestByDateThenVenuePreservingOrder() {
        LocalDate newer = LocalDate.of(2026, 7, 4);
        LocalDate older = LocalDate.of(2026, 6, 21);

        List<RaceResultRecord> records = List.of(
                raceRecord(newer, "東京", 1, "新東京1R", "馬1", 1, 1),
                raceRecord(newer, "東京", 2, "新東京2R", "馬2", 1, 1),
                raceRecord(newer, "函館", 1, "新函館1R", "馬3", 1, 1),
                raceRecord(older, "函館", 9, "旧函館9R", "馬4", 1, 1)
        );

        List<RaceResultGroup> groups = statsService.buildRaceGroups(records, List.of());
        Map<LocalDate, Map<String, List<RaceResultGroup>>> grouped =
                statsService.groupRacesByDateAndVenue(groups);

        assertEquals(List.of(newer, older), new ArrayList<>(grouped.keySet()));

        Map<String, List<RaceResultGroup>> newerByVenue = grouped.get(newer);
        assertEquals(2, newerByVenue.size());
        assertEquals(2, newerByVenue.get("東京").size());
        assertEquals(1, newerByVenue.get("函館").size());

        Map<String, List<RaceResultGroup>> olderByVenue = grouped.get(older);
        assertEquals(1, olderByVenue.size());
        assertEquals("旧函館9R", olderByVenue.get("函館").get(0).getRaceName());
    }

    private RaceResultRecord modelRecord(
            LocalDate date, String modelVersion, int predictionRank, double odds, int actualRank) {

        return new RaceResultRecord(
                date, "東京", 1, "テストレース", "馬", odds, predictionRank, 50, actualRank,
                0, predictionRank, modelVersion, null);
    }

    @Test
    void buildModelStat_shouldAggregateOnlyTopPicksOfThatModel() {
        // 予想1位のみが集計対象(2位以下のレコードは無視される)。
        // 3レースのうち1レース的中(オッズ4.0)、1頭が3着内 -> 的中率33.3%、回収率4.0/3=133.3%
        List<RaceResultRecord> records = List.of(
                modelRecord(LocalDate.of(2026, 9, 20), "v2", 1, 4.0, 1),
                modelRecord(LocalDate.of(2026, 9, 20), "v2", 2, 10.0, 2),
                modelRecord(LocalDate.of(2026, 9, 21), "v2", 1, 6.0, 3),
                modelRecord(LocalDate.of(2026, 9, 27), "v2", 1, 8.0, 5)
        );

        RaceResultStatsService.ModelStat stat = statsService.buildModelStat("v2", records);

        assertEquals("v2", stat.getLabel());
        assertEquals(3, stat.getRaceCount());
        assertEquals(100.0 / 3, stat.getWinRate(), 0.001);
        assertEquals(200.0 / 3, stat.getTop3Rate(), 0.001);
        assertEquals(4.0 / 3 * 100, stat.getRoi(), 0.001);
        assertEquals(LocalDate.of(2026, 9, 20), stat.getFirstDate());
        assertEquals(LocalDate.of(2026, 9, 27), stat.getLastDate());
        assertEquals(3, stat.getOddsBandStats().size());
    }

    @Test
    void buildModelStat_shouldLabelNullVersionAsLegacyModel() {
        RaceResultRecord legacy = modelRecord(LocalDate.of(2026, 7, 4), null, 1, 5.0, 1);

        RaceResultStatsService.ModelStat stat = statsService.buildModelStat(null, List.of(legacy));

        assertEquals(RaceResultStatsService.LEGACY_MODEL_LABEL, stat.getLabel());
        assertNull(stat.getModelVersion());
    }

    @Test
    void buildModelStat_shouldFlagSmallSampleBelowTargetRaceCount() {
        List<RaceResultRecord> few = List.of(modelRecord(LocalDate.of(2026, 9, 20), "v2", 1, 5.0, 1));

        assertTrue(statsService.buildModelStat("v2", few).isSmallSample());
    }

    @Test
    void buildModelStat_shouldNotFlagSmallSampleAtTargetRaceCount() {
        List<RaceResultRecord> enough = new ArrayList<>();
        for (int i = 0; i < RaceResultStatsService.MODEL_EVAL_TARGET_RACES; i++) {
            enough.add(modelRecord(LocalDate.of(2026, 9, 20), "v2", 1, 5.0, 2));
        }

        assertFalse(statsService.buildModelStat("v2", enough).isSmallSample());
    }

    @Test
    void buildModelStat_shouldReturnZeroRatesWhenNoRecords() {
        RaceResultStatsService.ModelStat stat = statsService.buildModelStat("v2", List.of());

        assertEquals(0, stat.getRaceCount());
        assertEquals(0, stat.getWinRate());
        assertEquals(0, stat.getRoi());
        assertNull(stat.getFirstDate());
        assertTrue(stat.isSmallSample());
    }

    @Test
    void sortModelStats_shouldPutCurrentFirstAndLegacyLast() {
        RaceResultStatsService.ModelStat legacy = statsService.buildModelStat(
                null, List.of(modelRecord(LocalDate.of(2026, 7, 4), null, 1, 5.0, 1)));
        RaceResultStatsService.ModelStat older = statsService.buildModelStat(
                "v1", List.of(modelRecord(LocalDate.of(2026, 9, 13), "v1", 1, 5.0, 1)));
        RaceResultStatsService.ModelStat current = statsService.buildModelStat(
                "v2", List.of(modelRecord(LocalDate.of(2026, 9, 20), "v2", 1, 5.0, 1)));

        List<RaceResultStatsService.ModelStat> sorted =
                statsService.sortModelStats(List.of(legacy, older, current), "v2");

        assertEquals("v2", sorted.get(0).getModelVersion());
        assertEquals("v1", sorted.get(1).getModelVersion());
        assertNull(sorted.get(2).getModelVersion());
    }

    private static final LocalDate PAYOUT_DATE = LocalDate.of(2026, 9, 20);

    private RaceResultRecord pick(int raceNumber, int actualRank, Integer umaban) {
        return new RaceResultRecord(
                PAYOUT_DATE, "阪神", raceNumber, "テストレース", "馬", 5.0, 1, 50, actualRank,
                0, 1, "v2", umaban);
    }

    private RacePayout payout(int raceNumber, String betType, String combination, int yen) {
        return new RacePayout(PAYOUT_DATE, "阪神", raceNumber, betType, combination, yen);
    }

    // 複勝は結果ページの掲載順(=着順)で、1着馬・2着馬・3着馬の順に払戻行が並ぶ
    private List<RacePayout> placePayouts(int raceNumber, int first, int second, int third) {
        return List.of(
                payout(raceNumber, "複勝", "8", first),
                payout(raceNumber, "複勝", "2", second),
                payout(raceNumber, "複勝", "4", third));
    }

    @Test
    void calculateRoiForBetType_shouldAddPayoutWhenPlaceBetHits() {
        // 2着に入った馬(馬番2)の複勝払戻120円 -> 100円購入で120円回収、回収率120%
        List<RaceResultRecord> picks = List.of(pick(4, 2, 2));

        double roi = statsService.calculateRoiForBetType(
                picks, placePayouts(4, 190, 120, 830), "複勝");

        assertEquals(120.0, roi, 0.001);
    }

    @Test
    void calculateRoiForBetType_shouldReturnZeroWhenPlaceBetMisses() {
        // 4着(複勝の払戻対象外)は的中しないので回収額0、購入はカウントされる
        List<RaceResultRecord> picks = List.of(pick(4, 4, 3));

        RaceResultStatsService.BetTypeRoi result = statsService.buildBetTypeRoi(
                picks, placePayouts(4, 190, 120, 830), "複勝");

        assertEquals(1, result.getBetCount());
        assertEquals(0, result.getHitCount());
        assertEquals(0.0, result.getRoi(), 0.001);
    }

    @Test
    void calculateRoiForBetType_shouldExcludeRacesWithoutPayoutDataFromStake() {
        // 払戻データが無いレース(取得失敗等)は購入していないものとして扱い、投資額にも含めない。
        // 含めると的中レース1つ(120円)+データ欠損1レースで回収率が60%に見えてしまう
        List<RaceResultRecord> picks = List.of(pick(4, 2, 2), pick(5, 1, 8));

        RaceResultStatsService.BetTypeRoi result = statsService.buildBetTypeRoi(
                picks, placePayouts(4, 190, 120, 830), "複勝");

        assertEquals(1, result.getBetCount());
        assertEquals(120.0, result.getRoi(), 0.001);
    }

    @Test
    void calculateRoiForBetType_shouldReturnZeroWhenNoPayoutDataAtAll() {
        double roi = statsService.calculateRoiForBetType(List.of(pick(4, 1, 8)), List.of(), "複勝");

        assertEquals(0.0, roi, 0.001);
    }

    @Test
    void calculateRoiForBetType_shouldFallBackToFinishOrderWhenUmabanIsMissing() {
        // 馬番を記録し始める前の旧レコード(umaban=null)は、着順がk着ならk番目の払戻行とみなす。
        // 3着(払戻行の3番目=830円)に入ったケース
        List<RaceResultRecord> picks = List.of(pick(4, 3, null));

        double roi = statsService.calculateRoiForBetType(
                picks, placePayouts(4, 190, 120, 830), "複勝");

        assertEquals(830.0, roi, 0.001);
    }

    @Test
    void calculateRoiForBetType_shouldNotPayThirdPlaceWhenOnlyTwoPlacesArePaid() {
        // 7頭立て以下は複勝が2着以内のみで払戻行が2つ。3着は的中にならない
        List<RacePayout> twoPlaces = List.of(
                payout(4, "複勝", "8", 190),
                payout(4, "複勝", "2", 120));

        RaceResultStatsService.BetTypeRoi result = statsService.buildBetTypeRoi(
                List.of(pick(4, 3, 4)), twoPlaces, "複勝");

        assertEquals(0, result.getHitCount());
    }

    @Test
    void calculateRoiForBetType_shouldComputeWinBetFromConfirmedPayout() {
        List<RacePayout> win = List.of(payout(4, "単勝", "8", 1300), payout(4, "複勝", "8", 190));

        double roi = statsService.calculateRoiForBetType(List.of(pick(4, 1, 8)), win, "単勝");

        assertEquals(1300.0, roi, 0.001);
    }

    @Test
    void calculateRoiForBetType_shouldRejectUnsupportedBetType() {
        assertThrows(IllegalArgumentException.class,
                () -> statsService.calculateRoiForBetType(List.of(), List.of(), "馬連"));
    }
}

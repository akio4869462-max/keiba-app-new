package org.example.keibaapp;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BettingSimulationServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 4);

    private final BettingSimulationService service =
            new BettingSimulationService(new RaceResultStatsService());

    private final BettingSimulationService.BettingRule rule =
            BettingSimulationService.BettingRule.defaultRule();

    // 人気=予想順位=馬番(1〜n)の素直な出走表。overlays[i]が馬番i+1のoverlay、ranks[i]が着順
    private List<RaceResultRecord> race(int raceNumber, double[] overlays, int[] ranks) {
        List<RaceResultRecord> records = new ArrayList<>();

        for (int i = 0; i < overlays.length; i++) {
            int n = i + 1;
            records.add(new RaceResultRecord(
                    DATE, "東京", raceNumber, "テストレース", "馬" + n, 2.0 * n, n, 50,
                    ranks[i], overlays[i], n, "v2", n));
        }

        return records;
    }

    private double[] flat(int size) {
        return new double[size];
    }

    // 馬番5(人気5・妙味0.1)だけが推奨条件を満たす8頭立て
    private double[] oneRecommendedAtFifth() {
        double[] overlays = flat(8);
        overlays[4] = 0.1;
        return overlays;
    }

    private RacePayout payout(int raceNumber, String betType, String combination, int yen) {
        return new RacePayout(DATE, "東京", raceNumber, betType, combination, yen);
    }

    @Test
    void selectRecommended_shouldApplyPopularityBandThresholdAndFieldSize() {
        double[] overlays = flat(8);
        overlays[0] = 0.5;   // 1番人気: 人気帯の外
        overlays[3] = 0.05;  // 4番人気: 閾値(0.074)未満
        overlays[4] = 0.1;   // 5番人気: 該当
        List<RaceResultRecord> records = race(1, overlays, new int[]{1, 2, 3, 4, 5, 6, 7, 8});

        List<RaceResultRecord> recommended = service.selectRecommended(records, rule);

        assertEquals(1, recommended.size());
        assertEquals(5, recommended.get(0).getUmaban());
    }

    @Test
    void selectRecommended_shouldExcludeSmallField() {
        // 7頭立て(最小頭数8未満)は、条件を満たす馬がいても推奨しない
        double[] overlays = flat(7);
        overlays[4] = 0.1;
        List<RaceResultRecord> records = race(1, overlays, new int[]{1, 2, 3, 4, 5, 6, 7});

        assertTrue(service.selectRecommended(records, rule).isEmpty());
    }

    @Test
    void selectRecommended_shouldIgnoreRecordsWithoutOverlayOrPopularity() {
        // overlay・人気を記録し始める前の旧レコードはnullで、推奨判定の対象外
        RaceResultRecord legacy = new RaceResultRecord();
        ReflectionTestUtils.setField(legacy, "odds", 5.0);
        ReflectionTestUtils.setField(legacy, "raceDate", DATE);

        assertTrue(service.selectRecommended(List.of(legacy), rule).isEmpty());
    }

    @Test
    void simulate_shouldComputeWinAndPlaceFromRecommendedHorses() {
        // 推奨馬(馬番5)が1着。単勝600円・複勝200円 -> 回収率600%/200%
        List<RaceResultRecord> records = race(1, oneRecommendedAtFifth(), new int[]{3, 4, 2, 6, 1, 5, 7, 8});
        List<RacePayout> payouts = List.of(
                payout(1, "単勝", "5", 600),
                payout(1, "複勝", "5", 200),
                payout(1, "複勝", "3", 150),
                payout(1, "複勝", "1", 120));

        BettingSimulationService.SimulationResult result = service.simulate(records, payouts, rule);

        assertEquals(1, result.recommendedCount());
        assertEquals(1, result.raceCount());
        assertEquals(600.0, result.results().get(0).getRoi(), 0.001);
        assertEquals(200.0, result.results().get(1).getRoi(), 0.001);
    }

    @Test
    void simulate_trioAxis_shouldHitWhenAxisAndTwoPartnersFillTop3() {
        // 軸=馬番5。相手は予想順位上位の1〜4,6番(5頭)。着順は5→1→3で、軸と相手2頭(1,3)が3着以内
        List<RaceResultRecord> records = race(1, oneRecommendedAtFifth(), new int[]{2, 4, 3, 6, 1, 5, 7, 8});
        List<RacePayout> payouts = List.of(payout(1, "3連複", "1-3-5", 12340));

        RaceResultStatsService.BetTypeRoi trio = service.simulate(records, payouts, rule).results().get(2);

        assertEquals(BettingSimulationService.BET_TYPE_TRIO_AXIS, trio.getBetType());
        assertEquals(1, trio.getBetCount());
        assertEquals(1, trio.getHitCount());
        assertEquals(1000, trio.getTotalStakeYen());   // C(5,2)=10点 x 100円
        assertEquals(12340, trio.getTotalReturnYen());
        assertEquals(1234.0, trio.getRoi(), 0.001);
    }

    @Test
    void simulate_trioAxis_shouldMissWhenOnlyOnePartnerReachesTop3() {
        // 3着が相手に選ばれていない馬(予想順位7番の馬番7)なので、軸+相手2頭にならず不的中
        List<RaceResultRecord> records = race(1, oneRecommendedAtFifth(), new int[]{2, 4, 7, 6, 1, 5, 3, 8});
        List<RacePayout> payouts = List.of(payout(1, "3連複", "1-5-7", 9999));

        RaceResultStatsService.BetTypeRoi trio = service.simulate(records, payouts, rule).results().get(2);

        assertEquals(1, trio.getBetCount());
        assertEquals(0, trio.getHitCount());
        assertEquals(0, trio.getTotalReturnYen());
        assertEquals(1000, trio.getTotalStakeYen());
    }

    @Test
    void simulate_trioAxis_shouldSkipRaceWithoutPayoutData() {
        List<RaceResultRecord> records = race(1, oneRecommendedAtFifth(), new int[]{2, 4, 3, 6, 1, 5, 7, 8});

        RaceResultStatsService.BetTypeRoi trio = service.simulate(records, List.of(), rule).results().get(2);

        assertEquals(0, trio.getBetCount());
        assertEquals(0, trio.getTotalStakeYen());
    }

    @Test
    void simulate_trioAxis_shouldUsePartnerCountFromRule() {
        // 相手3頭ならC(3,2)=3点=300円。相手は予想順位上位の1,2,3番(軸の5番を除く)
        List<RaceResultRecord> records = race(1, oneRecommendedAtFifth(), new int[]{2, 4, 3, 6, 1, 5, 7, 8});
        List<RacePayout> payouts = List.of(payout(1, "3連複", "1-3-5", 5000));
        BettingSimulationService.BettingRule threePartners =
                new BettingSimulationService.BettingRule(2, 8, 0.074, 8, 3);

        RaceResultStatsService.BetTypeRoi trio = service.simulate(records, payouts, threePartners).results().get(2);

        assertEquals(300, trio.getTotalStakeYen());
        assertEquals(1, trio.getHitCount());
    }

    @Test
    void simulate_shouldReturnEmptyResultsWhenNothingRecommended() {
        List<RaceResultRecord> records = race(1, flat(8), new int[]{1, 2, 3, 4, 5, 6, 7, 8});

        BettingSimulationService.SimulationResult result = service.simulate(records, List.of(), rule);

        assertEquals(0, result.recommendedCount());
        assertTrue(result.isSmallSample());
        assertNull(result.firstDate());
        assertEquals(0, result.results().get(0).getBetCount());
    }
}

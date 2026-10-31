package org.example.keibaapp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ForwardEvaluationServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 17);

    private final ForwardEvaluationService service = new ForwardEvaluationService();

    private OddsSnapshot snapshot(int raceNumber, int umaban, int popularity, double overlay,
                                  boolean recommended, double marketProbability) {
        return new OddsSnapshot(DATE, "東京", raceNumber, umaban, "馬" + umaban, 10.0, null, null,
                10.0, marketProbability, overlay, popularity, recommended, "v3",
                DATE.atTime(10, 0));
    }

    private RacePayout winPayout(int raceNumber, int umaban, int yen) {
        return new RacePayout(DATE, "東京", raceNumber, "単勝", String.valueOf(umaban), yen);
    }

    @Test
    void evaluate_shouldComputeHitsAndRoiForRecommendedHorses() {
        List<OddsSnapshot> snapshots = List.of(
                snapshot(1, 5, 4, 0.2, true, 10.0),    // 的中(払戻1,200円)
                snapshot(2, 3, 6, 0.2, true, 8.0),     // 外れ
                snapshot(2, 1, 1, 0.0, false, 30.0));  // 推奨ではない(1番人気)
        List<RacePayout> payouts = List.of(
                winPayout(1, 5, 1200),
                winPayout(2, 1, 250));

        ForwardEvaluationService.Evaluation evaluation = service.evaluate(snapshots, payouts);
        ForwardEvaluationService.GroupResult recommended = evaluation.groups().get(0);

        assertEquals(2, recommended.picks());
        assertEquals(1, recommended.hits());
        assertEquals(200, recommended.stakeYen());
        assertEquals(1200, recommended.returnYen());
        assertEquals(600.0, recommended.roi(), 1e-9);
        assertEquals(50.0, recommended.hitRate(), 1e-9);
        // 実現率 = 的中数 / 市場が見込む的中数(0.10 + 0.08)
        assertEquals(1 / 0.18, recommended.realizedRatio(), 1e-9);
        assertEquals(2, evaluation.evaluatedRaces());
        assertTrue(evaluation.smallSample());
    }

    @Test
    void evaluate_shouldSkipRacesWithoutPayoutData() {
        List<OddsSnapshot> snapshots = List.of(
                snapshot(1, 5, 4, 0.2, true, 10.0),
                snapshot(2, 3, 6, 0.2, true, 8.0));   // 2Rは払戻金データ無し(結果未収集)

        ForwardEvaluationService.Evaluation evaluation = service.evaluate(snapshots, List.of(winPayout(1, 9, 500)));

        assertEquals(1, evaluation.groups().get(0).picks());
        assertEquals(2, evaluation.snapshotRaces());
        assertEquals(1, evaluation.evaluatedRaces());
    }

    @Test
    void evaluate_shouldIgnoreNonWinPayoutsAndGroupByPopularityBand() {
        List<OddsSnapshot> snapshots = List.of(
                snapshot(1, 1, 1, 0.3, false, 40.0),    // 1番人気: 基準にも含めない
                snapshot(1, 2, 2, 0.12, false, 20.0),   // 妙味10%以上・2〜8番人気
                snapshot(1, 3, 3, 0.02, false, 15.0),   // 基準のみ
                snapshot(1, 4, 9, 0.5, false, 3.0));    // 9番人気: 対象外
        List<RacePayout> payouts = new ArrayList<>(List.of(
                new RacePayout(DATE, "東京", 1, "複勝", "2", 150),   // 複勝は無視
                winPayout(1, 2, 640)));

        ForwardEvaluationService.Evaluation evaluation = service.evaluate(snapshots, payouts);

        assertEquals(0, evaluation.groups().get(0).picks());     // 推奨フラグなし
        assertEquals(1, evaluation.groups().get(1).picks());
        assertEquals(1, evaluation.groups().get(1).hits());
        assertEquals(2, evaluation.groups().get(2).picks());     // 2番人気と3番人気
        assertEquals(640, evaluation.groups().get(2).returnYen());
        assertEquals(320.0, evaluation.groups().get(2).roi(), 1e-9);
    }

    @Test
    void evaluate_shouldHandleEmptyInput() {
        ForwardEvaluationService.Evaluation evaluation = service.evaluate(List.of(), List.of());

        assertEquals(0, evaluation.evaluatedRaces());
        assertNull(evaluation.firstDate());
        assertEquals(0.0, evaluation.groups().get(0).roi());
        assertEquals(0.0, evaluation.groups().get(0).realizedRatio());
    }

    // ---- OddsSnapshotService ----

    private static final Clock CLOCK = Clock.fixed(
            DATE.atTime(10, 0).atZone(ZoneId.of("Asia/Tokyo")).toInstant(), ZoneId.of("Asia/Tokyo"));

    private Horse horse(String umaban, double odds, int popularity) {
        Horse h = new Horse(umaban, umaban, "馬" + umaban, "騎手", "57.0", odds);
        h.setPopularity(popularity);
        h.setPredictionScore(12.5);
        h.setMarketProbability(10.0);
        h.setOverlay(0.25);
        h.setRecommended(true);
        return h;
    }

    private MorningOddsRepository emptyMorning() {
        MorningOddsRepository morning = mock(MorningOddsRepository.class);
        when(morning.findByRaceDateAndVenueAndRaceNumber(any(), anyString(), anyInt())).thenReturn(List.of());
        return morning;
    }

    private RaceInfo race() {
        return new RaceInfo(11, "東京", "テストS", java.time.LocalTime.of(15, 40), "芝", "1600m", List.of());
    }

    @Test
    void record_shouldSaveValidHorsesWithEarlierOddsAndSkipInvalid() {
        OddsSnapshotRepository repository = mock(OddsSnapshotRepository.class);
        when(repository.existsByRaceDateAndVenueAndRaceNumber(any(), anyString(), anyInt())).thenReturn(false);
        OddsSnapshotService snapshotService = new OddsSnapshotService(repository, emptyMorning(), CLOCK);

        Horse invalid = horse("3", 0, 0);   // 取消(オッズ無効 → 人気0)
        Horse valid = horse("2", 6.0, 3);
        valid.setFukuGap(0.1);

        int saved = snapshotService.record(race(), List.of(horse("2", 8.0, 4), horse("3", 5.0, 2)), List.of(valid, invalid));

        assertEquals(1, saved);
        org.mockito.ArgumentCaptor<List<OddsSnapshot>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        OddsSnapshot snap = captor.getValue().get(0);
        assertEquals(2, snap.getUmaban());
        assertEquals(6.0, snap.getWinOdds());
        assertEquals(8.0, snap.getEarlierWinOdds());   // 直前の一覧取得時点のオッズ
        assertEquals(0.1, snap.getFukuGap());
        assertEquals(PredictionService.MODEL_VERSION, snap.getModelVersion());
        assertEquals(DATE, snap.getRaceDate());
    }

    @Test
    void record_shouldNotSaveTwiceForTheSameRaceAndDay() {
        OddsSnapshotRepository repository = mock(OddsSnapshotRepository.class);
        when(repository.existsByRaceDateAndVenueAndRaceNumber(DATE, "東京", 11)).thenReturn(true);

        int saved = new OddsSnapshotService(repository, emptyMorning(), CLOCK).record(race(), List.of(), List.of(horse("1", 3.0, 1)));

        assertEquals(0, saved);
        verify(repository, never()).saveAll(any());
    }

    @Test
    void record_shouldTagPhaseAndMinutesToPostAndGuardPerPhase() {
        OddsSnapshotRepository repository = mock(OddsSnapshotRepository.class);
        // 10分前の記録は済んでいるが、3分前の記録はまだ
        when(repository.existsByRaceDateAndVenueAndRaceNumber(DATE, "東京", 11)).thenReturn(true);
        when(repository.existsByRaceDateAndVenueAndRaceNumberAndPhase(DATE, "東京", 11, "T3")).thenReturn(false);
        // 15:37(発走15:40の3分前)
        Clock nearClose = Clock.fixed(DATE.atTime(15, 37).atZone(ZoneId.of("Asia/Tokyo")).toInstant(), ZoneId.of("Asia/Tokyo"));
        OddsSnapshotService snapshotService = new OddsSnapshotService(repository, emptyMorning(), nearClose);

        assertEquals(0, snapshotService.record(race(), List.of(), List.of(horse("1", 3.0, 1))));   // T10は記録済みなので保存しない
        assertEquals(1, snapshotService.record(race(), List.of(), List.of(horse("1", 3.0, 1)), OddsSnapshotService.PHASE_T3));

        org.mockito.ArgumentCaptor<List<OddsSnapshot>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        OddsSnapshot snap = captor.getValue().get(0);
        assertEquals("T3", snap.getPhase());
        assertEquals(3.0, snap.getMinutesToPost(), 1e-9);
    }

    @Test
    void evaluate_shouldIgnoreThreeMinuteSnapshotsButExportKeepsThem() {
        OddsSnapshot tenMinutes = snapshot(1, 5, 4, 0.2, true, 10.0);                                 // 段階なし(旧行)=10分前
        OddsSnapshot closing = snapshot(1, 5, 4, 0.2, true, 10.0).withPhase("T3", 3.0);
        List<RacePayout> payouts = List.of(winPayout(1, 5, 1200));

        ForwardEvaluationService.Evaluation evaluation = service.evaluate(List.of(tenMinutes, closing), payouts);

        // 同じ馬を二重に数えない
        assertEquals(1, evaluation.groups().get(0).picks());

        String csv = service.exportCsv(List.of(tenMinutes, closing), payouts);
        String[] lines = csv.split("\n");
        assertEquals(3, lines.length);
        assertTrue(lines[0].contains(",captured_at,phase,minutes_to_post,won,win_payout_yen"), lines[0]);
        assertTrue(lines[1].contains(",T10,"), lines[1]);
        assertTrue(lines[2].contains(",T3,3.0,"), lines[2]);
    }

    @Test
    void compare_shouldUseOnlyRacesWithBothPhasesAndPayoutAndKeepRacesAligned() {
        // 1R: 10分前と3分前の両方あり・払戻あり → 比較の対象。10分前は的中、3分前は別の馬を選んだので外れ
        OddsSnapshot r1Ten = snapshot(1, 5, 4, 0.2, true, 10.0);
        OddsSnapshot r1Close = snapshot(1, 6, 5, 0.2, true, 10.0).withPhase("T3", 3.0);
        // 2R: 10分前しか無い → 対象外
        OddsSnapshot r2Ten = snapshot(2, 3, 4, 0.2, true, 10.0);
        // 3R: 両方あるが払戻データが無い → 対象外
        OddsSnapshot r3Ten = snapshot(3, 1, 4, 0.2, true, 10.0);
        OddsSnapshot r3Close = snapshot(3, 1, 4, 0.2, true, 10.0).withPhase("T3", 3.0);
        List<RacePayout> payouts = List.of(winPayout(1, 5, 1200), winPayout(2, 3, 800));

        ForwardEvaluationService.Comparison comparison = service.compare(
                List.of(r1Ten, r1Close, r2Ten, r3Ten, r3Close), payouts);

        assertEquals(1, comparison.races());
        ForwardEvaluationService.GroupResult ten = comparison.tenMinute().get(0);
        ForwardEvaluationService.GroupResult close = comparison.close().get(0);
        assertEquals(1, ten.picks());
        assertEquals(1, ten.hits());
        assertEquals(1200, ten.returnYen());
        assertEquals(1, close.picks());
        assertEquals(0, close.hits());
        assertEquals(0, close.returnYen());
        assertTrue(comparison.smallSample());
        // 10分前と3分前で、同じ3グループが同じ順に並ぶ
        assertEquals(3, comparison.tenMinute().size());
        assertEquals(comparison.tenMinute().get(2).label(), comparison.close().get(2).label());
    }

    @Test
    void compare_shouldBeEmptyWhenNoRaceHasBothPhases() {
        ForwardEvaluationService.Comparison comparison = service.compare(
                List.of(snapshot(1, 5, 4, 0.2, true, 10.0)), List.of(winPayout(1, 5, 1200)));

        assertEquals(0, comparison.races());
        assertEquals(0, comparison.tenMinute().get(0).picks());
    }
}

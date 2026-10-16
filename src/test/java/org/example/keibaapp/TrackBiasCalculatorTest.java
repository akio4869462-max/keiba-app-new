package org.example.keibaapp;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TrackBiasCalculatorTest {

    // 馬番1〜n、オッズは人気順に増える。着順は馬番順(=人気順)
    private List<ResultPageInfo.ResultRow> rowsInOrder(int n) {
        List<ResultPageInfo.ResultRow> rows = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            rows.add(new ResultPageInfo.ResultRow(i, i, 2.0 + i));
        }
        return rows;
    }

    @Test
    void raceBias_shouldBePositiveWhenOuterHorsesBeatTheMarket() {
        // 市場(オッズ)の想定は馬番順だが、実際の着順は外を回った馬ほど上位 → 外有利 (b>0)
        Map<Integer, Double> lat = new HashMap<>();
        int n = 12;

        for (int i = 1; i <= n; i++) {
            lat.put(i, (double) (i % 4));
        }

        // 着順を lat の降順に並べ直す(lat が大きい馬ほど上位)
        List<ResultPageInfo.ResultRow> reordered = new ArrayList<>();
        List<Integer> byLat = new ArrayList<>(lat.keySet());
        byLat.sort((a, b) -> Double.compare(lat.get(b), lat.get(a)) != 0
                ? Double.compare(lat.get(b), lat.get(a)) : Integer.compare(a, b));

        for (int rank = 1; rank <= n; rank++) {
            int umaban = byLat.get(rank - 1);
            reordered.add(new ResultPageInfo.ResultRow(umaban, rank, 2.0 + umaban));
        }

        TrackBiasCalculator.RaceBias bias = TrackBiasCalculator.raceBias(reordered, lat);

        assertTrue(bias.hasBias());
        assertTrue(bias.bm() > 0, "外を回した馬が好走したレースではbmは正になるはず: " + bias.bm());
        assertTrue(bias.se() > 0);
    }

    @Test
    void raceBias_shouldReturnNoBiasWhenTooFewHorses() {
        Map<Integer, Double> lat = new HashMap<>();
        for (int i = 1; i <= 5; i++) {
            lat.put(i, (double) i);
        }

        TrackBiasCalculator.RaceBias bias = TrackBiasCalculator.raceBias(rowsInOrder(5), lat);

        assertFalse(bias.hasBias());
        // バイアスが推定できなくても、レース内の平均latは前走の外回し度合いに使うので返す
        assertEquals(3.0, bias.meanLat(), 1e-9);
    }

    @Test
    void raceBias_shouldReturnNoBiasWhenLatDoesNotVary() {
        Map<Integer, Double> lat = new HashMap<>();
        for (int i = 1; i <= 10; i++) {
            lat.put(i, 0.0);
        }

        assertFalse(TrackBiasCalculator.raceBias(rowsInOrder(10), lat).hasBias());
    }

    @Test
    void raceBias_shouldIgnoreScratchedAndNonFinishers() {
        Map<Integer, Double> lat = new HashMap<>();
        List<ResultPageInfo.ResultRow> rows = new ArrayList<>(rowsInOrder(10));
        for (int i = 1; i <= 10; i++) {
            lat.put(i, (double) (i % 3));
        }
        // 取消(オッズ無し)と中止(着順0)が混じっても計算できる
        rows.add(new ResultPageInfo.ResultRow(11, 0, 0.0));
        rows.add(new ResultPageInfo.ResultRow(12, 0, 30.0));
        lat.put(12, 1.0);

        TrackBiasCalculator.RaceBias bias = TrackBiasCalculator.raceBias(rows, lat);

        assertTrue(bias.hasBias());
    }

    @Test
    void dayBias_shouldReproduceRealKokuraTurfValue() {
        // 2026-07-12 小倉・芝の実データ(TARGET側 bias_v4.py の day_lat_loo4 = 0.027971 と一致する)。
        // 1Rを除く他の芝レース(bm, se)の精度重み平均を、前年平均prior=-0.023491へ縮小する
        List<TrackBiasCalculator.RaceBias> others = List.of(
                new TrackBiasCalculator.RaceBias(0.108421, 0.104909, 0.0),
                new TrackBiasCalculator.RaceBias(0.149392, 0.118143, 0.0),
                new TrackBiasCalculator.RaceBias(0.125881, 0.173464, 0.0),
                new TrackBiasCalculator.RaceBias(0.289898, 0.314997, 0.0),
                new TrackBiasCalculator.RaceBias(-0.412264, 0.166400, 0.0),
                new TrackBiasCalculator.RaceBias(-0.260798, 0.204392, 0.0),
                new TrackBiasCalculator.RaceBias(0.213703, 0.106591, 0.0));

        assertEquals(0.027971, TrackBiasCalculator.dayBias(others, -0.023491), 2e-5);
    }

    @Test
    void dayBias_shouldFallBackToPriorWhenNoOtherRaceHasBias() {
        List<TrackBiasCalculator.RaceBias> others = List.of(new TrackBiasCalculator.RaceBias(null, null, 0.4));

        assertEquals(-0.03, TrackBiasCalculator.dayBias(others, -0.03), 1e-12);
        assertEquals(-0.03, TrackBiasCalculator.dayBias(List.of(), -0.03), 1e-12);
    }

    @Test
    void dayBias_shouldWeightPreciseRacesMoreHeavily() {
        // seが小さい(精度が高い)レースの値に近づく
        List<TrackBiasCalculator.RaceBias> others = List.of(
                new TrackBiasCalculator.RaceBias(0.2, 0.05, 0.0),
                new TrackBiasCalculator.RaceBias(-0.2, 0.5, 0.0));

        assertTrue(TrackBiasCalculator.dayBias(others, 0.0) > 0.1);
    }
}

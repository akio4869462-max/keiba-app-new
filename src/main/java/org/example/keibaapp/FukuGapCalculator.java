package org.example.keibaapp;

import java.util.HashMap;
import java.util.Map;

// 複勝ギャップ(MODEL_REVISION.md §8.12)の計算。純粋な計算のみ(通信・DBなし)。
//
// 締切10分前の単勝と複勝を比べ、「複勝プールで単勝より相対的に人気している馬」を拾う。
// 複勝プールが単勝より先に情報を織り込み、単勝があとから追いつくため、正のギャップを持つ馬は
// 単勝オッズが締切にかけて下がりやすく、勝率も市場の想定を上回る(8年データの検証、
// keiba_score_search/analysis/explore_fuku_gap.py と fit_fuku_gap_weights.py)。
//
// gap_raw = logit(複勝mid由来の相対支持) - logit(単勝の市場確率)  (どちらもレース内正規化)
// gap     = gap_raw - (c0 + c1·logit(単勝) + c2·logit(単勝)^2)    (人気薄ほど複勝が高く出る分を除く)
final class FukuGapCalculator {

    // 学習時(TRAIN 2019-22)の2次式の係数。fit_fuku_gap_weights.py の出力
    static final double C0 = -0.9374510036093507;
    static final double C1 = -0.5804661335558046;
    static final double C2 = -0.05292217608627081;

    // 推定に必要な最低頭数(学習データも5頭以上のレースのみ)
    static final int MIN_HORSES = 5;

    private FukuGapCalculator() {
    }

    // winOdds: 馬番→単勝オッズ、placeMid: 馬番→複勝オッズの下限と上限の中間。
    // どちらも有効(>0)な馬だけを対象にし、頭数が足りなければ空のMapを返す
    static Map<Integer, Double> compute(Map<Integer, Double> winOdds, Map<Integer, Double> placeMid) {
        Map<Integer, Double> valid = new HashMap<>();

        for (Map.Entry<Integer, Double> entry : winOdds.entrySet()) {
            Double mid = placeMid.get(entry.getKey());

            if (entry.getValue() != null && entry.getValue() > 0 && mid != null && mid > 0) {
                valid.put(entry.getKey(), entry.getValue());
            }
        }

        if (valid.size() < MIN_HORSES) {
            return Map.of();
        }

        double winInverseSum = 0;
        double placeInverseSum = 0;

        for (Integer umaban : valid.keySet()) {
            winInverseSum += 1.0 / winOdds.get(umaban);
            placeInverseSum += 1.0 / placeMid.get(umaban);
        }

        Map<Integer, Double> result = new HashMap<>();

        for (Integer umaban : valid.keySet()) {
            double winLogit = logit((1.0 / winOdds.get(umaban)) / winInverseSum);
            double placeLogit = logit((1.0 / placeMid.get(umaban)) / placeInverseSum);

            double expected = C0 + C1 * winLogit + C2 * winLogit * winLogit;
            result.put(umaban, placeLogit - winLogit - expected);
        }

        return result;
    }

    private static double logit(double q) {
        double clipped = Math.min(Math.max(q, 1e-6), 1 - 1e-6);
        return Math.log(clipped / (1 - clipped));
    }
}

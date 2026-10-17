package org.example.keibaapp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 馬連・ワイド・馬単の板から「馬ごとの強さ(シェア)」を作る。純粋な計算のみ(通信・DBなし)。
// keiba_score_search/analysis/explore_pool_gap.py の定義と同じ:
//   馬連・ワイド: 各組の 1/オッズ(ワイドは下限と上限の中間)を両方の馬に足し、レース内で合計1に正規化
//   馬単      : 各組(1着→2着)の 1/オッズを1着側の馬だけに足して正規化(単勝と同じ「1着の強さ」)
// 確定時点の板では、これらと単勝との食い違いが勝ちを予測した(MODEL_REVISION.md §8.14)。
// 10分前でも効くかは前向きに記録して確かめる段階なので、まだモデルには使わない
final class PoolShareCalculator {

    private PoolShareCalculator() {
    }

    // 馬連・ワイドのように順序の無い組の板。両方の馬に加算する
    static Map<Integer, Double> symmetricShares(List<WebScraper.PairOdds> pairs) {
        Map<Integer, Double> sums = new HashMap<>();

        for (WebScraper.PairOdds pair : pairs) {
            if (pair.odds() > 0) {
                sums.merge(pair.first(), 1.0 / pair.odds(), Double::sum);
                sums.merge(pair.second(), 1.0 / pair.odds(), Double::sum);
            }
        }

        return normalize(sums);
    }

    // 馬単のように順序のある組の板。1着側の馬だけに加算する
    static Map<Integer, Double> firstShares(List<WebScraper.PairOdds> pairs) {
        Map<Integer, Double> sums = new HashMap<>();

        for (WebScraper.PairOdds pair : pairs) {
            if (pair.odds() > 0) {
                sums.merge(pair.first(), 1.0 / pair.odds(), Double::sum);
            }
        }

        return normalize(sums);
    }

    private static Map<Integer, Double> normalize(Map<Integer, Double> sums) {
        double total = 0;
        for (double v : sums.values()) {
            total += v;
        }

        Map<Integer, Double> shares = new HashMap<>();

        if (total > 0) {
            for (Map.Entry<Integer, Double> entry : sums.entrySet()) {
                shares.put(entry.getKey(), entry.getValue() / total);
            }
        }

        return shares;
    }
}

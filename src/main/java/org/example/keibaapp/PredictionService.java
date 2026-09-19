package org.example.keibaapp;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// 予想モデル(2026-09-19改訂、keiba_score_search/analysis/MODEL_REVISION.md準拠)。
// 単勝オッズが織り込む「市場確率」をレース内softmaxで勝率に変換するだけの式で、
// 距離適性・コース適性・枠順・騎手成績等の加点は行わない。
// 8年分(2019-2026、約40万出走)のオフライン検証で、これらの加点が
// 市場情報を制御した後は有害またはノイズと判定されたため
// (favorite-longshot bias: 加点により人気薄が上位に来やすくなり、
// 8年データで13番人気以下の単勝ROIは-42%、220倍超は-63%)。
@Service
public class PredictionService {

    public static final String MODEL_VERSION = "2026-09-19-market-softmax-v1";

    // レース内softmaxで市場確率(q)を勝率(p)へ変換する係数(2019-22学習データでの推定値)。
    // 追加の特徴量が入るまではこの1項のみのため、
    // 実質「人気順をそのまま確率に変換する」式になる(意図的な仕様)
    private static final double MARKET_WEIGHT = 0.885;

    // 妙味(overlay = p/q - 1)がこの値以上、かつ2〜8番人気の馬だけを
    // 「買い候補」として示す(forward_rules.json "2-8_0.95"より)。
    // 2019-22学習→2023-26検証でも信頼区間は0をまたぎ、断定的な推奨ではない参考値
    static final double OVERLAY_THRESHOLD = 0.342;
    static final int RECOMMEND_POPULARITY_MIN = 2;
    static final int RECOMMEND_POPULARITY_MAX = 8;
    private static final int MIN_FIELD_SIZE_FOR_RECOMMEND = 8;

    private boolean hasValidOdds(Horse horse) {
        return horse.getOdds() > 0 && horse.getOdds() < 999.9;
    }

    // レース内の全馬について市場確率(q)・モデル勝率(p)・妙味(overlay)・人気・
    // 買い候補フラグを算出しHorseにセットする。
    // pはレース内softmaxで決まるため、必ずレース単位(そのレースの全馬)で呼ぶこと
    public void applyRaceModel(List<Horse> horses) {
        List<Horse> validHorses = new ArrayList<>();

        for (Horse horse : horses) {
            if (hasValidOdds(horse)) {
                validHorses.add(horse);
            } else {
                horse.setMarketProbability(0);
                horse.setPredictionScore(0);
                horse.setOverlay(0);
                horse.setPopularity(0);
                horse.setRecommended(false);
                horse.setPredictionReason("オッズ評価なし(取消・除外等)");
            }
        }

        if (validHorses.isEmpty()) {
            return;
        }

        double oddsInverseSum = 0;
        for (Horse horse : validHorses) {
            oddsInverseSum += 1.0 / horse.getOdds();
        }

        double[] winProbabilities = calculateWinProbabilities(validHorses, oddsInverseSum);

        List<Horse> byPopularity = new ArrayList<>(validHorses);
        byPopularity.sort(Comparator.comparingDouble(Horse::getOdds));

        for (int i = 0; i < validHorses.size(); i++) {
            Horse horse = validHorses.get(i);

            double q = (1.0 / horse.getOdds()) / oddsInverseSum;
            double p = winProbabilities[i];
            double overlay = p / q - 1;
            int popularity = byPopularity.indexOf(horse) + 1;

            boolean recommended = validHorses.size() >= MIN_FIELD_SIZE_FOR_RECOMMEND
                    && popularity >= RECOMMEND_POPULARITY_MIN
                    && popularity <= RECOMMEND_POPULARITY_MAX
                    && overlay >= OVERLAY_THRESHOLD;

            horse.setMarketProbability(q * 100);
            horse.setPredictionScore(p * 100);
            horse.setOverlay(overlay);
            horse.setPopularity(popularity);
            horse.setRecommended(recommended);
            horse.setPredictionReason(createReason(popularity, q, p, overlay, recommended));
        }
    }

    // 単勝オッズ1頭だけでは勝率が決まらない(1頭立てはq=1になり logit が発散する)ため、
    // 有効頭数が1のときは特別扱いする
    private double[] calculateWinProbabilities(List<Horse> validHorses, double oddsInverseSum) {
        int n = validHorses.size();
        double[] p = new double[n];

        if (n == 1) {
            p[0] = 1.0;
            return p;
        }

        double[] expScores = new double[n];
        double sum = 0;

        for (int i = 0; i < n; i++) {
            double q = (1.0 / validHorses.get(i).getOdds()) / oddsInverseSum;
            double logit = Math.log(q / (1 - q));
            expScores[i] = Math.exp(MARKET_WEIGHT * logit);
            sum += expScores[i];
        }

        for (int i = 0; i < n; i++) {
            p[i] = expScores[i] / sum;
        }

        return p;
    }

    private String createReason(int popularity, double q, double p, double overlay, boolean recommended) {
        StringBuilder reason = new StringBuilder();

        reason.append(popularity).append("番人気")
                .append("(市場確率 ").append(String.format("%.1f", q * 100)).append("%)");

        reason.append("\nモデル勝率 ").append(String.format("%.1f", p * 100)).append("%");

        reason.append("\n妙味(overlay) ")
                .append(overlay >= 0 ? "+" : "")
                .append(String.format("%.1f", overlay * 100)).append("%");

        if (recommended) {
            reason.append("\n※妙味・人気帯の条件を満たす買い候補(参考値。信頼区間は0をまたぐため断定はできません)");
        }

        return reason.toString();
    }
}

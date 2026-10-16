package org.example.keibaapp;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// 予想モデル(2026-09-19改訂、keiba_score_search/analysis/MODEL_REVISION.md準拠)。
// 単勝オッズが織り込む「市場確率」をレース内softmaxで勝率に変換する式に、
// 父・騎手・生産者の市場相対残差(§8)と前走の走路バイアス(§8.11)という、
// 市場情報を制御しても残る数少ない有効なシグナルを加える。距離適性・コース適性・枠順・騎手成績そのもの等の加点は
// 行わない。8年分(2019-2026、約40万出走)のオフライン検証で、これらの加点が
// 市場情報を制御した後は有害またはノイズと判定されたため
// (favorite-longshot bias: 加点により人気薄が上位に来やすくなり、
// 8年データで13番人気以下の単勝ROIは-42%、220倍超は-63%)。
@Service
public class PredictionService {

    public static final String MODEL_VERSION = "2026-10-18-market-residual-bias-fuku-v3";

    // レース内softmaxで市場確率(q)を勝率(p)へ変換する係数(2019-22学習データでの推定値)。
    private static final double MARKET_WEIGHT = 0.885;

    // 前走の走路バイアス特徴の重みと標準化定数(MODEL_REVISION.md §8.11、
    // keiba_score_search の fit_bias_weights.py。TRAIN 2019-22で市場+父・騎手・生産者
    // +この2項を勝ちの条件付きロジットで同時フィットした値)。s_i に
    // weight * clip((raw - mean) / sd, ±BIAS_Z_CLIP) を加算する。特徴が無ければ寄与0。
    // p_hw_lat は裾が重く(zで±10超)、外れ値が妙味を歪めるためzを制限している
    private static final double W_HW_LAT = 0.056;
    private static final double HW_LAT_MEAN = -0.000053;
    private static final double HW_LAT_SD = 0.028542;
    private static final double W_LAT_C = 0.0272;
    private static final double LAT_C_MEAN = 0.004691;
    private static final double LAT_C_SD = 0.52549;
    private static final double BIAS_Z_CLIP = 4.0;
    // 複勝ギャップ(FukuGapCalculator、§8.12)。発走10分前の再取得時だけ付与される
    private static final double W_FUKU_GAP = 0.0420;
    private static final double FUKU_GAP_SD = 0.2154776;

    private final MarketResidualService marketResidualService;

    public PredictionService(MarketResidualService marketResidualService) {
        this.marketResidualService = marketResidualService;
    }

    // 妙味(overlay = p/q - 1)がこの値以上、かつ2〜8番人気の馬だけを
    // 「買い候補」として示す。
    // 元々の0.342は「市場+13シグナル+走路バイアス族」というリッチなモデルの
    // overlay分布で較正された値で、本番実装(市場+父・騎手・生産者の3項のみ)には
    // スケールが合わず実質発火しなかった(8年・2〜8番人気198,210頭中22頭=0.011%のみ)。
    // 実装済み構成の重み(residual_weights.json)でoverlay分布を再計算し、
    // 2〜8番人気のTRAIN(2019-22)期間95%分位に再較正した値
    // (MODEL_REVISION.md §8.8、2026-09-20)。
    // 2019-22学習→2023-26検証でも信頼区間は0をまたぎ、断定的な推奨ではない参考値。
    // 前走の走路バイアス2項を加えた構成ではoverlayの分布が広がるため、同じ手続きで
    // 再較正した(2〜8番人気のTRAIN 95%分位、検証期間でも0.140でほぼ一致。§8.11)
    static final double OVERLAY_THRESHOLD = 0.142;
    // 発走10分前の再取得で複勝ギャップが付いたレースは、項が増えてoverlayの分布がさらに広がるため、
    // 同じ手続きで再較正した閾値を使う(2〜8番人気のTRAIN 95%分位0.166、検証期間0.160。§8.12)
    static final double OVERLAY_THRESHOLD_WITH_FUKU_GAP = 0.166;
    static final int RECOMMEND_POPULARITY_MIN = 2;
    static final int RECOMMEND_POPULARITY_MAX = 8;
    static final int MIN_FIELD_SIZE_FOR_RECOMMEND = 8;

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
        double overlayThreshold = validHorses.stream().anyMatch(h -> h.getFukuGap() != null)
                ? OVERLAY_THRESHOLD_WITH_FUKU_GAP
                : OVERLAY_THRESHOLD;

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
                    && overlay >= overlayThreshold;

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
            Horse horse = validHorses.get(i);
            double q = (1.0 / horse.getOdds()) / oddsInverseSum;
            double logit = Math.log(q / (1 - q));

            double residualScore = marketResidualService.sireScore(horse.getSire())
                    + marketResidualService.jockeyScore(horse.getJockeyName())
                    + marketResidualService.breederScore(horse.getBreeder());

            expScores[i] = Math.exp(MARKET_WEIGHT * logit + residualScore + trackBiasScore(horse) + fukuGapScore(horse));
            sum += expScores[i];
        }

        for (int i = 0; i < n; i++) {
            p[i] = expScores[i] / sum;
        }

        return p;
    }

    // 前走の走路バイアス特徴(p_hw_lat: 前走で馬場の流れに逆らって走らされた度合い、
    // p_lat_c: 前走で外を回した度合い)のスコア。TrackBiasServiceが付与しなかった馬は0
    static double trackBiasScore(Horse horse) {
        return biasTerm(horse.getPHwLat(), W_HW_LAT, HW_LAT_MEAN, HW_LAT_SD)
                + biasTerm(horse.getPLatC(), W_LAT_C, LAT_C_MEAN, LAT_C_SD);
    }

    static double fukuGapScore(Horse horse) {
        return biasTerm(horse.getFukuGap(), W_FUKU_GAP, 0.0, FUKU_GAP_SD);
    }

    private static double biasTerm(Double raw, double weight, double mean, double sd) {
        if (raw == null || raw.isNaN()) {
            return 0;
        }

        double z = (raw - mean) / sd;

        return weight * Math.max(-BIAS_Z_CLIP, Math.min(BIAS_Z_CLIP, z));
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

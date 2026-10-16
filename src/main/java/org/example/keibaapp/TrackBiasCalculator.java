package org.example.keibaapp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// 走路バイアス(MODEL_REVISION.md §3.1/§6.2)の計算。keiba_score_search/analysis/bias_v4.py の
// race_stats と day_lat_loo4 の定義を移植したもので、純粋な計算だけを持つ(通信・DBなし)。
//
// 1レース分: 着順パーセンタイル perf を市場(オッズ)で回帰した残差 e が、
// コーナーで外を回った度合い lat_mean とどれだけ相関するか(b = cov(e, lat)/var(lat))と、
// その標準誤差 se を求める。b>0 ならその日は外を回った馬が市場の想定より好走した(外有利)。
final class TrackBiasCalculator {

    // 1レースでバイアスを推定するのに必要な最低頭数(着順・lat・オッズが揃った馬)
    static final int MIN_HORSES = 6;
    // 標準誤差の下限(これより小さくしても精度重みが際限なく大きくならないようにする)
    static final double SE_FLOOR = 0.02;
    // 同日のバイアスを前年の場×芝ダ平均(prior)へ縮小する重み
    static final double SHRINK_WEIGHT = 3.0;

    private TrackBiasCalculator() {
    }

    // bm/seは推定できなければnull。meanLatは(推定できなくても)レース内の平均lat
    record RaceBias(Double bm, Double se, Double meanLat) {
        boolean hasBias() {
            return bm != null && se != null;
        }
    }

    static RaceBias raceBias(List<ResultPageInfo.ResultRow> rows, Map<Integer, Double> latMeans) {
        List<ResultPageInfo.ResultRow> valid = new ArrayList<>();

        for (ResultPageInfo.ResultRow row : rows) {
            if (row.odds() > 0) {
                valid.add(row);
            }
        }

        // レース内の平均lat(前走の外回し度合い lat_c = 馬のlat - この平均 の基準)
        double latSum = 0;
        int latCount = 0;

        for (ResultPageInfo.ResultRow row : valid) {
            Double lat = latMeans.get(row.umaban());
            if (lat != null) {
                latSum += lat;
                latCount++;
            }
        }

        Double meanLat = latCount > 0 ? latSum / latCount : null;
        int n = valid.size();

        if (n < 2) {
            return new RaceBias(null, null, meanLat);
        }

        // 市場の勝率(q)をレース内で正規化し、そのlogitをレース内でz化する(mkt_z)
        double inverseSum = 0;
        for (ResultPageInfo.ResultRow row : valid) {
            inverseSum += 1.0 / row.odds();
        }

        double[] logit = new double[n];
        double logitMean = 0;

        for (int i = 0; i < n; i++) {
            double q = (1.0 / valid.get(i).odds()) / inverseSum;
            q = Math.min(Math.max(q, 1e-6), 1 - 1e-6);
            logit[i] = Math.log(q / (1 - q));
            logitMean += logit[i];
        }

        logitMean /= n;

        double logitVariance = 0;
        for (double v : logit) {
            logitVariance += (v - logitMean) * (v - logitMean);
        }

        double logitSd = Math.sqrt(logitVariance / (n - 1));
        if (logitSd == 0) {
            logitSd = 1;
        }

        List<double[]> samples = new ArrayList<>();   // {mkt_z, perf, lat}

        for (int i = 0; i < n; i++) {
            ResultPageInfo.ResultRow row = valid.get(i);
            Double lat = latMeans.get(row.umaban());

            if (row.rank() <= 0 || lat == null) {
                continue;
            }

            double perf = 1 - (row.rank() - 1.0) / Math.max(n - 1, 1);
            samples.add(new double[]{(logit[i] - logitMean) / logitSd, perf, lat});
        }

        int m = samples.size();

        if (m < MIN_HORSES) {
            return new RaceBias(null, null, meanLat);
        }

        double xMean = 0;
        double yMean = 0;
        double latMean = 0;

        for (double[] s : samples) {
            xMean += s[0];
            yMean += s[1];
            latMean += s[2];
        }

        xMean /= m;
        yMean /= m;
        latMean /= m;

        // perf ~ 1 + mkt_z の最小二乗残差 e
        double sxx = 0;
        double sxy = 0;

        for (double[] s : samples) {
            sxx += (s[0] - xMean) * (s[0] - xMean);
            sxy += (s[0] - xMean) * (s[1] - yMean);
        }

        double slope = sxx > 0 ? sxy / sxx : 0;
        double[] residual = new double[m];

        for (int i = 0; i < m; i++) {
            double[] s = samples.get(i);
            residual[i] = s[1] - (yMean + slope * (s[0] - xMean));
        }

        // e ~ lat_mean の傾き b と標準誤差 se
        double latSxx = 0;
        double latSxe = 0;

        for (int i = 0; i < m; i++) {
            double c = samples.get(i)[2] - latMean;
            latSxx += c * c;
            latSxe += c * residual[i];
        }

        if (latSxx == 0) {
            return new RaceBias(null, null, meanLat);
        }

        double b = latSxe / latSxx;
        double rss = 0;

        for (int i = 0; i < m; i++) {
            double c = samples.get(i)[2] - latMean;
            double res = residual[i] - b * c;
            rss += res * res;
        }

        double se = Math.sqrt(rss / Math.max(m - 2, 1) / latSxx);

        return new RaceBias(b, se, meanLat);
    }

    // 同じ日・場・芝ダの「他のレース」のバイアスを精度重み(1/se^2)で平均し、
    // 前年の場×芝ダ平均(prior)へ SHRINK_WEIGHT 分だけ縮小した推定値。
    // 対象レース自身を含めない(leave-one-out)ことで、自分のレース結果が自分の特徴量に
    // 漏れ込まないようにする。バイアスが推定できたレースが無ければpriorそのものになる
    static double dayBias(List<RaceBias> otherRaces, double prior) {
        double weightedSum = 0;
        double weightSum = 0;

        for (RaceBias race : otherRaces) {
            if (!race.hasBias()) {
                continue;
            }

            double w = 1.0 / Math.pow(Math.max(race.se(), SE_FLOOR), 2) / 100.0;
            weightedSum += w * race.bm();
            weightSum += w;
        }

        return (weightedSum + SHRINK_WEIGHT * prior) / (weightSum + SHRINK_WEIGHT);
    }
}

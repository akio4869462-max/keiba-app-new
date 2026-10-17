package org.example.keibaapp;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

// 前向きの評価: 発走10分前に残した予想(OddsSnapshot)の馬を単勝100円ずつ買っていたら、
// 確定払戻(RacePayout)でどうなったか。バックテストと違い、買う時点のオッズ・ギャップ・
// 推奨フラグをそのまま使うので、後付けの絞り込みが入らない。
//
// 先行指標として「実現率」(的中数 ÷ 市場が見込む的中数)も出す。控除率20%を踏まえると
// 損益分岐は実現率 1.25(MODEL_REVISION.md §5)。回収率より少ないサンプルで傾向が見える
@Service
public class ForwardEvaluationService {

    // これ未満の購入数では回収率の誤差が大きく判断できない(1,000点で±0.15程度のため、目安として300点を境に注記)
    static final int MIN_RELIABLE_PICKS = 300;
    static final int STAKE_YEN = 100;
    static final double TOP_OVERLAY_THRESHOLD = 0.10;

    public record GroupResult(String label, int picks, int hits, double expectedHits, long stakeYen, long returnYen) {

        public double hitRate() {
            return picks == 0 ? 0 : hits * 100.0 / picks;
        }

        // 実現率 = 的中数 / 市場が見込む的中数。1.25以上で控除率を超える
        public double realizedRatio() {
            return expectedHits == 0 ? 0 : hits / expectedHits;
        }

        // 回収率(100%が収支トントン)
        public double roi() {
            return stakeYen == 0 ? 0 : returnYen * 100.0 / stakeYen;
        }
    }

    public record Evaluation(int snapshotRaces, int evaluatedRaces, LocalDate firstDate, LocalDate lastDate,
                             List<GroupResult> groups, boolean smallSample) {
    }

    // 評価(回収率・実現率)の対象は10分前の記録だけ。3分前の記録(T3)は同じ馬を二重に数えないよう除く
    // (3分前との比較はオフラインのeval_forward_csv.py --phase T3 で行う)。段階が無い旧行は10分前
    private static boolean isTenMinuteSnapshot(OddsSnapshot s) {
        return s.getPhase() == null || OddsSnapshotService.PHASE_T10.equals(s.getPhase());
    }

    public Evaluation evaluate(List<OddsSnapshot> allSnapshots, List<RacePayout> payouts) {
        List<OddsSnapshot> snapshots = allSnapshots.stream().filter(ForwardEvaluationService::isTenMinuteSnapshot).toList();
        // (日付|開催場|R) → 馬番 → 単勝払戻額(100円あたり)
        Map<String, Map<Integer, Integer>> winPayouts = new HashMap<>();

        for (RacePayout payout : payouts) {
            if (!"単勝".equals(payout.getBetType()) || !payout.getCombination().trim().matches("\\d+")) {
                continue;
            }

            winPayouts.computeIfAbsent(raceKey(payout.getRaceDate(), payout.getVenue(), payout.getRaceNumber()),
                    k -> new HashMap<>()).put(Integer.parseInt(payout.getCombination().trim()), payout.getPayoutYen());
        }

        List<OddsSnapshot> evaluable = new ArrayList<>();
        java.util.Set<String> snapshotRaceKeys = new java.util.HashSet<>();
        java.util.Set<String> evaluatedRaceKeys = new java.util.HashSet<>();
        LocalDate first = null;
        LocalDate last = null;

        for (OddsSnapshot snapshot : snapshots) {
            String key = raceKey(snapshot.getRaceDate(), snapshot.getVenue(), snapshot.getRaceNumber());
            snapshotRaceKeys.add(key);

            // 払戻金が無いレースは(結果が未収集なので)購入しなかったものとして除く
            if (winPayouts.containsKey(key)) {
                evaluable.add(snapshot);
                evaluatedRaceKeys.add(key);

                if (first == null || snapshot.getRaceDate().isBefore(first)) {
                    first = snapshot.getRaceDate();
                }
                if (last == null || snapshot.getRaceDate().isAfter(last)) {
                    last = snapshot.getRaceDate();
                }
            }
        }

        List<GroupResult> groups = new ArrayList<>();
        groups.add(group("買い候補(推奨馬)", evaluable, winPayouts, s -> Boolean.TRUE.equals(s.getRecommended())));
        groups.add(group("妙味 " + (int) (TOP_OVERLAY_THRESHOLD * 100) + "%以上・2〜8番人気", evaluable, winPayouts,
                s -> inPopularityBand(s) && s.getOverlay() != null && s.getOverlay() >= TOP_OVERLAY_THRESHOLD));
        groups.add(group("2〜8番人気 全部(基準)", evaluable, winPayouts, ForwardEvaluationService::inPopularityBand));

        boolean small = groups.get(0).picks() < MIN_RELIABLE_PICKS;

        return new Evaluation(snapshotRaceKeys.size(), evaluatedRaceKeys.size(), first, last, groups, small);
    }

    // オフライン解析(keiba_score_search側)で、10分前の板から作ったシェアなどの効果を測るためのCSV。
    // 払戻金データがあるレースだけ、的中(won=1/0)と単勝払戻額(100円あたり、外れは0)を付ける。
    // 本番DBへ直接アクセスできないため、画面経由で取り出せるようにしてある
    public String exportCsv(List<OddsSnapshot> snapshots, List<RacePayout> payouts) {
        Map<String, Map<Integer, Integer>> winPayouts = new HashMap<>();

        for (RacePayout payout : payouts) {
            if ("単勝".equals(payout.getBetType()) && payout.getCombination().trim().matches("\\d+")) {
                winPayouts.computeIfAbsent(raceKey(payout.getRaceDate(), payout.getVenue(), payout.getRaceNumber()),
                        k -> new HashMap<>()).put(Integer.parseInt(payout.getCombination().trim()), payout.getPayoutYen());
            }
        }

        StringBuilder csv = new StringBuilder(
                "race_date,venue,race_number,umaban,win_odds,earlier_win_odds,morning_win_odds,morning_minutes_to_post,fuku_gap,win_probability,"
                        + "market_probability,overlay,popularity,recommended,umaren_share,wide_share,"
                        + "exacta_first_share,model_version,captured_at,phase,minutes_to_post,won,win_payout_yen\n");

        for (OddsSnapshot s : snapshots) {
            Map<Integer, Integer> racePayouts = winPayouts.get(raceKey(s.getRaceDate(), s.getVenue(), s.getRaceNumber()));
            String won = racePayouts == null ? "" : (racePayouts.containsKey(s.getUmaban()) ? "1" : "0");
            String payout = racePayouts == null ? "" : String.valueOf(racePayouts.getOrDefault(s.getUmaban(), 0));

            csv.append(String.join(",",
                    String.valueOf(s.getRaceDate()), s.getVenue(), String.valueOf(s.getRaceNumber()),
                    String.valueOf(s.getUmaban()), num(s.getWinOdds()), num(s.getEarlierWinOdds()),
                    num(s.getMorningWinOdds()), String.valueOf(s.getMorningMinutesToPost() == null ? "" : s.getMorningMinutesToPost()),
                    num(s.getFukuGap()), num(s.getWinProbability()), num(s.getMarketProbability()),
                    num(s.getOverlay()), String.valueOf(s.getPopularity()), String.valueOf(s.getRecommended()),
                    num(s.getUmarenShare()), num(s.getWideShare()), num(s.getExactaFirstShare()),
                    String.valueOf(s.getModelVersion()), String.valueOf(s.getCapturedAt()),
                    s.getPhase() == null ? OddsSnapshotService.PHASE_T10 : s.getPhase(),
                    num(s.getMinutesToPost()), won, payout)).append('\n');
        }

        return csv.toString();
    }

    private static String num(Double value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean inPopularityBand(OddsSnapshot s) {
        return s.getPopularity() != null && s.getPopularity() >= PredictionService.RECOMMEND_POPULARITY_MIN
                && s.getPopularity() <= PredictionService.RECOMMEND_POPULARITY_MAX;
    }

    private GroupResult group(String label, List<OddsSnapshot> snapshots,
                              Map<String, Map<Integer, Integer>> winPayouts, Predicate<OddsSnapshot> filter) {
        int picks = 0;
        int hits = 0;
        double expectedHits = 0;
        long returned = 0;

        for (OddsSnapshot s : snapshots) {
            if (!filter.test(s)) {
                continue;
            }

            picks++;
            expectedHits += s.getMarketProbability() == null ? 0 : s.getMarketProbability() / 100.0;

            Integer payout = winPayouts.get(raceKey(s.getRaceDate(), s.getVenue(), s.getRaceNumber())).get(s.getUmaban());

            if (payout != null) {
                hits++;
                // 払戻は100円あたり。購入額が100円なのでそのまま受け取る
                returned += payout;
            }
        }

        return new GroupResult(label, picks, hits, expectedHits, (long) picks * STAKE_YEN, returned);
    }

    private static String raceKey(LocalDate date, String venue, int raceNumber) {
        return date + "|" + (venue == null ? "" : venue.trim()) + "|" + raceNumber;
    }
}

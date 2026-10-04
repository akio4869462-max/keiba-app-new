package org.example.keibaapp;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 「推奨馬を実際に買っていたらどうなっていたか」の過去データでのシミュレーション。
// 買い方(何を推奨馬とみなすか・三連複で何頭へ流すか)はBettingRuleに切り出してあり、
// 実際の買い方に合わせて後から調整できる。
//
// 既存のRaceResultStatsServiceの計算パターンに合わせ、払戻金データが無いレースは
// 購入していないものとして投資額にも含めない。
// 推奨馬の判定にはoverlay・popularityが必要なため、それらを記録し始めた
// 2026-09-19以降のレコードだけがシミュレーション対象になる(旧レコードはnullで除外)。
@Service
public class BettingSimulationService {

    public static final String BET_TYPE_TRIO_AXIS = "3連複(1頭軸流し)";

    // 買い目が少ない間は回収率の誤差が大きいため参考値として扱う目安(推奨馬の頭数)
    public static final int MIN_RELIABLE_PICKS = 100;

    private static final String PAYOUT_TRIO = "3連複";

    private static final int STAKE_YEN = 100;

    private final RaceResultStatsService statsService;

    public BettingSimulationService(RaceResultStatsService statsService) {
        this.statsService = statsService;
    }

    // 推奨馬とみなす条件と、三連複流しの相手頭数。
    // デフォルトは予想画面の「買い候補」(PredictionService.recommended)と同じ条件
    public record BettingRule(
            int minPopularity,
            int maxPopularity,
            double overlayThreshold,
            int minFieldSize,
            int partnerCount) {

        public static BettingRule defaultRule() {
            return new BettingRule(
                    PredictionService.RECOMMEND_POPULARITY_MIN,
                    PredictionService.RECOMMEND_POPULARITY_MAX,
                    PredictionService.OVERLAY_THRESHOLD,
                    PredictionService.MIN_FIELD_SIZE_FOR_RECOMMEND,
                    5);
        }
    }

    public record SimulationResult(
            BettingRule rule,
            int recommendedCount,
            int raceCount,
            LocalDate firstDate,
            LocalDate lastDate,
            List<RaceResultStatsService.BetTypeRoi> results) {

        public boolean isSmallSample() {
            return recommendedCount < MIN_RELIABLE_PICKS;
        }

        public int getMinReliablePicks() {
            return MIN_RELIABLE_PICKS;
        }
    }

    private record RaceKey(LocalDate raceDate, String venue, int raceNumber) {
        static RaceKey of(RaceResultRecord r) {
            return new RaceKey(r.getRaceDate(), r.getVenue(), r.getRaceNumber());
        }
    }

    private boolean hasValidOdds(RaceResultRecord r) {
        return r.getOdds() > 0 && r.getOdds() < 999.9;
    }

    // PredictionService.applyRaceModelのrecommended判定を、保存済みのoverlay・人気から再現する。
    // 出走頭数はそのレースでオッズが有効な(取消・除外でない)レコード数
    public List<RaceResultRecord> selectRecommended(List<RaceResultRecord> records, BettingRule rule) {
        Map<RaceKey, Long> fieldSizes = new HashMap<>();

        for (RaceResultRecord r : records) {
            if (hasValidOdds(r)) {
                fieldSizes.merge(RaceKey.of(r), 1L, Long::sum);
            }
        }

        List<RaceResultRecord> recommended = new ArrayList<>();

        for (RaceResultRecord r : records) {
            if (r.getOverlay() == null || r.getPopularity() == null || !hasValidOdds(r)) {
                continue;
            }

            if (fieldSizes.getOrDefault(RaceKey.of(r), 0L) >= rule.minFieldSize()
                    && r.getPopularity() >= rule.minPopularity()
                    && r.getPopularity() <= rule.maxPopularity()
                    && r.getOverlay() >= rule.overlayThreshold()) {
                recommended.add(r);
            }
        }

        return recommended;
    }

    public SimulationResult simulate(
            List<RaceResultRecord> records, List<RacePayout> payouts, BettingRule rule) {

        List<RaceResultRecord> recommended = selectRecommended(records, rule);

        List<RaceResultStatsService.BetTypeRoi> results = new ArrayList<>();
        // 単勝・複勝は推奨馬1頭につき100円(的中判定・回収額は既存の券種別計算を再利用)
        results.add(statsService.buildBetTypeRoi(recommended, payouts, RaceResultStatsService.BET_TYPE_WIN));
        results.add(statsService.buildBetTypeRoi(recommended, payouts, RaceResultStatsService.BET_TYPE_PLACE));
        results.add(buildTrioAxisRoi(records, recommended, payouts, rule));

        long raceCount = recommended.stream().map(RaceKey::of).distinct().count();

        LocalDate firstDate = recommended.stream().map(RaceResultRecord::getRaceDate)
                .min(Comparator.naturalOrder()).orElse(null);
        LocalDate lastDate = recommended.stream().map(RaceResultRecord::getRaceDate)
                .max(Comparator.naturalOrder()).orElse(null);

        return new SimulationResult(rule, recommended.size(), (int) raceCount, firstDate, lastDate, results);
    }

    // 推奨馬を軸に、同じレースの予想順位上位(軸を除く)partnerCount頭へ流す三連複。
    // 相手がn頭ならC(n,2)点、1点100円。1レースに推奨馬が複数いれば、それぞれを軸として買う。
    // 三連複は馬番の組み合わせだが、的中は「軸と相手のうち2頭で3着以内が決まったか」と
    // 同じなので、着順だけで判定できる(馬番を記録していない旧レコードでも計算できる)。
    // 払戻額はそのレースの3連複の払戻行(1行)の金額
    private RaceResultStatsService.BetTypeRoi buildTrioAxisRoi(
            List<RaceResultRecord> allRecords,
            List<RaceResultRecord> recommended,
            List<RacePayout> allPayouts,
            BettingRule rule) {

        Map<RaceKey, RacePayout> trioPayouts = new HashMap<>();
        allPayouts.stream()
                .filter(p -> PAYOUT_TRIO.equals(p.getBetType()))
                .forEach(p -> trioPayouts.putIfAbsent(
                        new RaceKey(p.getRaceDate(), p.getVenue(), p.getRaceNumber()), p));

        Map<RaceKey, List<RaceResultRecord>> byRace = new HashMap<>();
        for (RaceResultRecord r : allRecords) {
            byRace.computeIfAbsent(RaceKey.of(r), k -> new ArrayList<>()).add(r);
        }

        int axisCount = 0;
        int hitCount = 0;
        long stake = 0;
        long totalReturn = 0;

        for (RaceResultRecord axis : recommended) {
            RaceKey key = RaceKey.of(axis);
            RacePayout payout = trioPayouts.get(key);

            if (payout == null) {
                continue;
            }

            List<RaceResultRecord> partners = byRace.getOrDefault(key, List.of()).stream()
                    .filter(r -> r != axis && hasValidOdds(r))
                    .sorted(Comparator.comparingInt(RaceResultRecord::getPredictionRank))
                    .limit(rule.partnerCount())
                    .toList();

            if (partners.size() < 2) {
                continue;
            }

            int points = partners.size() * (partners.size() - 1) / 2;
            axisCount++;
            stake += (long) points * STAKE_YEN;

            long partnersInTop3 = partners.stream()
                    .filter(r -> r.getActualRank() >= 1 && r.getActualRank() <= 3)
                    .count();

            if (axis.getActualRank() >= 1 && axis.getActualRank() <= 3 && partnersInTop3 >= 2) {
                hitCount++;
                totalReturn += payout.getPayoutYen();
            }
        }

        return new RaceResultStatsService.BetTypeRoi(BET_TYPE_TRIO_AXIS, axisCount, hitCount, stake, totalReturn);
    }
}

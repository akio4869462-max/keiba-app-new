package org.example.keibaapp;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RaceResultStatsService {

    private static final String[] SCORE_BAND_LABELS = {
            "上位25%(高スコア)", "26〜50%", "51〜75%", "下位25%(低スコア)"
    };

    public int countWins(List<RaceResultRecord> records) {
        return (int) records.stream()
                .filter(r -> r.getActualRank() == 1)
                .count();
    }

    public int countTop3(List<RaceResultRecord> records) {
        return (int) records.stream()
                .filter(r -> r.getActualRank() >= 1 && r.getActualRank() <= 3)
                .count();
    }

    public double rate(int hit, int total) {
        if (total == 0) {
            return 0;
        }

        return hit * 100.0 / total;
    }

    public String oddsBandLabel(double odds) {
        if (odds < 5) {
            return "人気馬(5倍未満)";
        }

        if (odds < 20) {
            return "中穴(5〜20倍)";
        }

        return "大穴(20倍超)";
    }

    public List<OddsBandStat> buildOddsBandStats(List<RaceResultRecord> topPicks) {
        Map<String, int[]> counts = new LinkedHashMap<>();
        counts.put("人気馬(5倍未満)", new int[3]);
        counts.put("中穴(5〜20倍)", new int[3]);
        counts.put("大穴(20倍超)", new int[3]);

        for (RaceResultRecord record : topPicks) {
            int[] c = counts.get(oddsBandLabel(record.getOdds()));
            c[0]++;

            if (record.getActualRank() == 1) {
                c[1]++;
            }

            if (record.getActualRank() >= 1 && record.getActualRank() <= 3) {
                c[2]++;
            }
        }

        List<OddsBandStat> stats = new ArrayList<>();

        for (Map.Entry<String, int[]> entry : counts.entrySet()) {
            int[] c = entry.getValue();
            stats.add(new OddsBandStat(entry.getKey(), c[0], rate(c[1], c[0]), rate(c[2], c[0])));
        }

        return stats;
    }

    public List<ScoreBandStat> buildScoreBandStats(List<RaceResultRecord> allRecords) {
        List<RaceResultRecord> sorted = allRecords.stream()
                .sorted(Comparator.comparingDouble(RaceResultRecord::getPredictionScore).reversed())
                .collect(Collectors.toList());

        List<ScoreBandStat> stats = new ArrayList<>();
        int total = sorted.size();

        if (total == 0) {
            return stats;
        }

        for (int i = 0; i < 4; i++) {
            int from = total * i / 4;
            int to = total * (i + 1) / 4;

            List<RaceResultRecord> band = sorted.subList(from, to);
            int bandTotal = band.size();
            int win = countWins(band);
            int top3 = countTop3(band);

            stats.add(new ScoreBandStat(SCORE_BAND_LABELS[i], bandTotal, rate(win, bandTotal), rate(top3, bandTotal)));
        }

        return stats;
    }

    public List<WeeklyStat> buildWeeklyStats(List<RaceResultRecord> topPicks) {
        WeekFields weekFields = WeekFields.ISO;

        Map<Integer, int[]> weekCounts = new HashMap<>();
        Map<Integer, String> weekLabels = new HashMap<>();

        for (RaceResultRecord record : topPicks) {
            LocalDate date = record.getRaceDate();
            int year = date.get(weekFields.weekBasedYear());
            int week = date.get(weekFields.weekOfWeekBasedYear());
            int key = year * 100 + week;

            weekLabels.putIfAbsent(key, year + "年第" + week + "週");

            int[] c = weekCounts.computeIfAbsent(key, k -> new int[2]);
            c[0]++;

            if (record.getActualRank() == 1) {
                c[1]++;
            }
        }

        List<Integer> sortedKeys = new ArrayList<>(weekCounts.keySet());
        sortedKeys.sort(Comparator.reverseOrder());

        List<WeeklyStat> stats = new ArrayList<>();

        for (Integer key : sortedKeys) {
            int[] c = weekCounts.get(key);
            stats.add(new WeeklyStat(weekLabels.get(key), c[0], rate(c[1], c[0])));
        }

        return stats;
    }

    public double calculateRoi(List<RaceResultRecord> topPicks) {
        double totalStake = topPicks.size();

        if (totalStake == 0) {
            return 0;
        }

        double totalReturn = totalReturn(topPicks);

        return totalReturn / totalStake * 100;
    }

    public List<RaceResultGroup> buildRaceGroups(List<RaceResultRecord> allRecords, List<RacePayout> allPayouts) {
        Map<RaceKey, List<RaceResultRecord>> grouped = allRecords.stream()
                .collect(Collectors.groupingBy(
                        r -> new RaceKey(r.getRaceDate(), r.getVenue(), r.getRaceNumber(), r.getRaceName())));

        Map<PayoutKey, List<RacePayout>> groupedPayouts = allPayouts.stream()
                .collect(Collectors.groupingBy(
                        p -> new PayoutKey(p.getRaceDate(), p.getVenue(), p.getRaceNumber())));

        List<RaceResultGroup> groups = new ArrayList<>();

        for (Map.Entry<RaceKey, List<RaceResultRecord>> entry : grouped.entrySet()) {
            List<RaceResultRecord> horses = new ArrayList<>(entry.getValue());
            horses.sort(Comparator.comparingInt(RaceResultRecord::getActualRank));

            RaceKey key = entry.getKey();
            PayoutKey payoutKey = new PayoutKey(key.raceDate(), key.venue(), key.raceNumber());
            List<RacePayout> payouts = groupedPayouts.getOrDefault(payoutKey, List.of());

            groups.add(new RaceResultGroup(
                    key.raceDate(), key.venue(), key.raceNumber(), key.raceName(), horses, payouts));
        }

        groups.sort(Comparator.comparing(RaceResultGroup::getRaceDate).reversed()
                .thenComparing(RaceResultGroup::getVenue)
                .thenComparingInt(RaceResultGroup::getRaceNumber));

        return groups;
    }

    private record RaceKey(LocalDate raceDate, String venue, int raceNumber, String raceName) {
    }

    private record PayoutKey(LocalDate raceDate, String venue, int raceNumber) {
    }

    public double totalReturn(List<RaceResultRecord> topPicks) {
        return topPicks.stream()
                .mapToDouble(r -> r.getActualRank() == 1 ? r.getOdds() : 0)
                .sum();
    }

    public static class OddsBandStat {
        private final String label;
        private final int total;
        private final double winRate;
        private final double top3Rate;

        public OddsBandStat(String label, int total, double winRate, double top3Rate) {
            this.label = label;
            this.total = total;
            this.winRate = winRate;
            this.top3Rate = top3Rate;
        }

        public String getLabel() {
            return label;
        }

        public int getTotal() {
            return total;
        }

        public double getWinRate() {
            return winRate;
        }

        public double getTop3Rate() {
            return top3Rate;
        }
    }

    public static class ScoreBandStat {
        private final String label;
        private final int total;
        private final double winRate;
        private final double top3Rate;

        public ScoreBandStat(String label, int total, double winRate, double top3Rate) {
            this.label = label;
            this.total = total;
            this.winRate = winRate;
            this.top3Rate = top3Rate;
        }

        public String getLabel() {
            return label;
        }

        public int getTotal() {
            return total;
        }

        public double getWinRate() {
            return winRate;
        }

        public double getTop3Rate() {
            return top3Rate;
        }
    }

    public static class WeeklyStat {
        private final String label;
        private final int total;
        private final double winRate;

        public WeeklyStat(String label, int total, double winRate) {
            this.label = label;
            this.total = total;
            this.winRate = winRate;
        }

        public String getLabel() {
            return label;
        }

        public int getTotal() {
            return total;
        }

        public double getWinRate() {
            return winRate;
        }
    }
}

package org.example.keibaapp;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class RaceController {

    // ★重要：Springに「サービスを使ってね」と伝えるための変数とコンストラクタ
    private final RaceService raceService;
    private final RaceNotificationService notificationService;
    private final DummyRaceFactory dummyRaceFactory;

    public RaceController(RaceService raceService, RaceNotificationService notificationService, DummyRaceFactory dummyRaceFactory) {
        this.raceService = raceService;
        this.notificationService = notificationService;
        this.dummyRaceFactory = dummyRaceFactory;
    }

    @GetMapping("/races")
    public String showRaces(Model model) {
        model.addAttribute("races", raceService.getRaces());
        return "raceList";
    }

    @GetMapping("/check")
    @ResponseBody
    public String checkNotification() {

        notificationService.checkFavorites();

        return "通知チェック完了";
    }

    @GetMapping("/check/debug")
    @ResponseBody
    public String checkDebugNotification() {
        List<RaceInfo> races = dummyRaceFactory.createDummyRaces();
        notificationService.checkFavoritesWithDummy(races);
        return "デバッグチェック完了";
    }

    @GetMapping("/results")
    public String results(Model model) {
        List<RaceInfo> races = raceService.fetchHistoricalRaces();

        int raceCount = races.size();
        int top1Win = 0;
        int top1Top3 = 0;
        int top3Top3 = 0;
        int top3Total = 0;

        Map<String, int[]> oddsBandCounts = new LinkedHashMap<>();
        oddsBandCounts.put("人気馬(3倍未満)", new int[3]);
        oddsBandCounts.put("中穴(3〜10倍)", new int[3]);
        oddsBandCounts.put("大穴(10倍超)", new int[3]);

        for (RaceInfo race : races) {
            List<Horse> horses = race.getHorses();

            if (horses.isEmpty()) {
                continue;
            }

            Horse topHorse = horses.get(0);

            if (topHorse.getActualRace() != null) {
                int rank = topHorse.getActualRace().getRank();

                if (rank == 1) {
                    top1Win++;
                }

                if (rank >= 1 && rank <= 3) {
                    top1Top3++;
                }

                int[] bandCounts = oddsBandCounts.get(oddsBandLabel(topHorse.getOdds()));
                bandCounts[0]++;

                if (rank == 1) {
                    bandCounts[1]++;
                }

                if (rank >= 1 && rank <= 3) {
                    bandCounts[2]++;
                }
            }

            for (int i = 0; i < Math.min(3, horses.size()); i++) {
                Horse horse = horses.get(i);

                if (horse.getActualRace() != null) {
                    top3Total++;

                    int rank = horse.getActualRace().getRank();

                    if (rank >= 1 && rank <= 3) {
                        top3Top3++;
                    }
                }
            }
        }

        model.addAttribute("races", races);
        model.addAttribute("raceCount", raceCount);
        model.addAttribute("top1WinRate", rate(top1Win, raceCount));
        model.addAttribute("top1Top3Rate", rate(top1Top3, raceCount));
        model.addAttribute("top3Top3Rate", rate(top3Top3, top3Total));

        List<OddsBandStat> oddsBandStats = new ArrayList<>();

        for (Map.Entry<String, int[]> entry : oddsBandCounts.entrySet()) {
            int[] counts = entry.getValue();
            oddsBandStats.add(new OddsBandStat(
                    entry.getKey(),
                    counts[0],
                    rate(counts[1], counts[0]),
                    rate(counts[2], counts[0])
            ));
        }

        model.addAttribute("oddsBandStats", oddsBandStats);

        return "results";
    }

    private double rate(int hit, int total) {
        if (total == 0) {
            return 0;
        }

        return hit * 100.0 / total;
    }

    private String oddsBandLabel(double odds) {
        if (odds < 3) {
            return "人気馬(3倍未満)";
        }

        if (odds < 10) {
            return "中穴(3〜10倍)";
        }

        return "大穴(10倍超)";
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
}
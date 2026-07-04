package org.example.keibaapp;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class RaceController {

    // ★重要：Springに「サービスを使ってね」と伝えるための変数とコンストラクタ
    private final RaceService raceService;
    private final RaceNotificationService notificationService;
    private final DummyRaceFactory dummyRaceFactory;
    private final RaceResultRecordRepository raceResultRecordRepository;
    private final RaceResultStatsService raceResultStatsService;
    private final RaceResultCollectionService raceResultCollectionService;

    public RaceController(
            RaceService raceService,
            RaceNotificationService notificationService,
            DummyRaceFactory dummyRaceFactory,
            RaceResultRecordRepository raceResultRecordRepository,
            RaceResultStatsService raceResultStatsService,
            RaceResultCollectionService raceResultCollectionService) {

        this.raceService = raceService;
        this.notificationService = notificationService;
        this.dummyRaceFactory = dummyRaceFactory;
        this.raceResultRecordRepository = raceResultRecordRepository;
        this.raceResultStatsService = raceResultStatsService;
        this.raceResultCollectionService = raceResultCollectionService;
    }

    @GetMapping("/results/collect")
    @ResponseBody
    public String collectResults() {
        return raceResultCollectionService.collectWeekendResults();
    }

    @GetMapping("/results/debug")
    @ResponseBody
    public String debugResults() {
        List<RaceResultRecord> topPicks = raceResultRecordRepository.findAll().stream()
                .filter(r -> r.getPredictionRank() == 1)
                .collect(Collectors.toList());

        Map<String, Long> byDateVenue = topPicks.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getRaceDate() + " " + r.getVenue(),
                        LinkedHashMap::new,
                        Collectors.counting()));

        StringBuilder sb = new StringBuilder();
        sb.append("合計レース数: ").append(topPicks.size()).append("\n\n");

        for (Map.Entry<String, Long> entry : byDateVenue.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("件\n");
        }

        return sb.toString();
    }

    @GetMapping("/races")
    public String showRaces(Model model) {
        List<RaceInfo> races = raceService.getBasicRaces();

        // LinkedHashMap を指定することで取得順（東京→阪神→小倉）を保持する
        Map<String, List<RaceInfo>> racesByVenue = races.stream()
                .collect(Collectors.groupingBy(
                        RaceInfo::getVenue,
                        LinkedHashMap::new,
                        Collectors.toList()));

        model.addAttribute("racesByVenue", racesByVenue);
        return "raceList";
    }

    @GetMapping("/predict")
    public String showPredict(Model model) {
        List<RaceInfo> races = raceService.getRaces();

        Map<String, List<RaceInfo>> racesByVenue = races.stream()
                .collect(Collectors.groupingBy(
                        RaceInfo::getVenue,
                        LinkedHashMap::new,
                        Collectors.toList()));

        model.addAttribute("racesByVenue", racesByVenue);
        return "predict";
    }

    @GetMapping("/check")
    @ResponseBody
    public String checkNotification() {

        // 手動実行時はキャッシュを最新化してから通知チェック
        // （自動スケジュール実行はPreloadServiceがキャッシュを管理するため不要）
        raceService.getRaces();
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
        List<RaceResultRecord> allRecords = raceResultRecordRepository.findAll();

        List<RaceResultRecord> topPicks = allRecords.stream()
                .filter(r -> r.getPredictionRank() == 1)
                .collect(Collectors.toList());

        List<RaceResultRecord> top3Picks = allRecords.stream()
                .filter(r -> r.getPredictionRank() <= 3)
                .collect(Collectors.toList());

        int raceCount = topPicks.size();
        int top1Win = raceResultStatsService.countWins(topPicks);
        int top1Top3 = raceResultStatsService.countTop3(topPicks);
        int top3Total = top3Picks.size();
        int top3Top3 = raceResultStatsService.countTop3(top3Picks);

        model.addAttribute("raceCount", raceCount);
        model.addAttribute("top1WinRate", raceResultStatsService.rate(top1Win, raceCount));
        model.addAttribute("top1Top3Rate", raceResultStatsService.rate(top1Top3, raceCount));
        model.addAttribute("top3Top3Rate", raceResultStatsService.rate(top3Top3, top3Total));

        model.addAttribute("oddsBandStats", raceResultStatsService.buildOddsBandStats(topPicks));
        model.addAttribute("scoreBandStats", raceResultStatsService.buildScoreBandStats(allRecords));
        model.addAttribute("weeklyStats", raceResultStatsService.buildWeeklyStats(topPicks));

        model.addAttribute("roi", raceResultStatsService.calculateRoi(topPicks));
        model.addAttribute("roiBetCount", topPicks.size());
        model.addAttribute("roiTotalReturn", raceResultStatsService.totalReturn(topPicks));

        return "results";
    }
}

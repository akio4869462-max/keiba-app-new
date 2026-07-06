package org.example.keibaapp;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class RaceController {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    // /results/racesはページが際限なく伸びないよう直近分のみ表示する
    private static final int RACE_RESULTS_DISPLAY_WEEKS = 4;

    // ★重要：Springに「サービスを使ってね」と伝えるための変数とコンストラクタ
    private final RaceService raceService;
    private final RaceNotificationService notificationService;
    private final DummyRaceFactory dummyRaceFactory;
    private final RaceResultRecordRepository raceResultRecordRepository;
    private final RacePayoutRepository racePayoutRepository;
    private final RaceResultStatsService raceResultStatsService;
    private final RaceResultCollectionService raceResultCollectionService;
    private final TrackedRaceUrlRepository trackedRaceUrlRepository;
    private final FavoriteHorseService favoriteHorseService;
    private final FavoriteJockeyService favoriteJockeyService;

    public RaceController(
            RaceService raceService,
            RaceNotificationService notificationService,
            DummyRaceFactory dummyRaceFactory,
            RaceResultRecordRepository raceResultRecordRepository,
            RacePayoutRepository racePayoutRepository,
            RaceResultStatsService raceResultStatsService,
            RaceResultCollectionService raceResultCollectionService,
            TrackedRaceUrlRepository trackedRaceUrlRepository,
            FavoriteHorseService favoriteHorseService,
            FavoriteJockeyService favoriteJockeyService) {

        this.raceService = raceService;
        this.notificationService = notificationService;
        this.dummyRaceFactory = dummyRaceFactory;
        this.raceResultRecordRepository = raceResultRecordRepository;
        this.racePayoutRepository = racePayoutRepository;
        this.raceResultStatsService = raceResultStatsService;
        this.raceResultCollectionService = raceResultCollectionService;
        this.trackedRaceUrlRepository = trackedRaceUrlRepository;
        this.favoriteHorseService = favoriteHorseService;
        this.favoriteJockeyService = favoriteJockeyService;
    }

    @GetMapping("/results/collect")
    @ResponseBody
    public String collectResults() {
        raceResultCollectionService.collectWeekendResultsAsync();
        return "結果収集を開始しました。1頭ずつ取得するため時間がかかります。"
                + "しばらくしてから/results/racesや/results/debugで確認してください。";
    }

    @GetMapping("/results/reset")
    @ResponseBody
    public String resetResults() {
        long deletedCount = raceResultRecordRepository.count();
        raceResultRecordRepository.deleteAll();

        List<TrackedRaceUrl> trackedUrls = trackedRaceUrlRepository.findAll();

        for (TrackedRaceUrl tracked : trackedUrls) {
            tracked.setProcessed(false);
        }

        trackedRaceUrlRepository.saveAll(trackedUrls);

        return "検証結果をリセットしました（削除件数: " + deletedCount + "件 / "
                + trackedUrls.size() + "件のURLを再収集対象に戻しました）";
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

    @GetMapping("/results/races")
    public String resultsByRace(Model model) {
        LocalDate cutoff = LocalDate.now(JST).minusWeeks(RACE_RESULTS_DISPLAY_WEEKS);
        List<RaceResultRecord> recentRecords = raceResultRecordRepository.findByRaceDateGreaterThanEqual(cutoff);
        List<RacePayout> recentPayouts = racePayoutRepository.findByRaceDateGreaterThanEqual(cutoff);

        model.addAttribute("raceGroups", raceResultStatsService.buildRaceGroups(recentRecords, recentPayouts));
        model.addAttribute("displayWeeks", RACE_RESULTS_DISPLAY_WEEKS);

        return "raceResults";
    }

    @GetMapping("/favorites/today")
    public String favoritesToday(Model model) {
        List<RaceInfo> races = raceService.getRaces();

        List<FavoriteRaceEntry> horseMatches = new ArrayList<>();

        for (FavoriteHorse favorite : favoriteHorseService.findAll()) {
            for (RaceInfo race : races) {
                for (Horse horse : race.getHorses()) {
                    if (favorite.getHorseName().equals(horse.getName())) {
                        horseMatches.add(new FavoriteRaceEntry(race, horse, favorite.getHorseName()));
                    }
                }
            }
        }

        List<FavoriteRaceEntry> jockeyMatches = new ArrayList<>();

        for (FavoriteJockey favorite : favoriteJockeyService.findAll()) {
            for (RaceInfo race : races) {
                for (Horse horse : race.getHorses()) {
                    if (favorite.getJockeyName().equals(horse.getJockeyName())) {
                        jockeyMatches.add(new FavoriteRaceEntry(race, horse, favorite.getJockeyName()));
                    }
                }
            }
        }

        Comparator<FavoriteRaceEntry> byRaceTime = Comparator.comparing(entry -> entry.getRace().getRaceTime());
        horseMatches.sort(byRaceTime);
        jockeyMatches.sort(byRaceTime);

        model.addAttribute("horseMatches", horseMatches);
        model.addAttribute("jockeyMatches", jockeyMatches);

        return "favoritesToday";
    }
}

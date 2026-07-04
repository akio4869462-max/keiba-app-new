package org.example.keibaapp;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

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
        model.addAttribute("races", raceService.getBasicRaces());
        return "raceList";
    }

    @GetMapping("/predict")
    public String showPredict(Model model) {
        model.addAttribute("races", raceService.getRaces());
        return "predict";
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

        return "results";
    }

    private double rate(int hit, int total) {
        if (total == 0) {
            return 0;
        }

        return hit * 100.0 / total;
    }
}
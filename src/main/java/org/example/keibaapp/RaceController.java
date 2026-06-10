package org.example.keibaapp;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class RaceController {

    // ★重要：Springに「サービスを使ってね」と伝えるための変数とコンストラクタ
    private final RaceService raceService;
    private final RaceNotificationService notificationService;

    public RaceController(RaceService raceService, RaceNotificationService notificationService) {
        this.raceService = raceService;
        this.notificationService = notificationService;
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
}
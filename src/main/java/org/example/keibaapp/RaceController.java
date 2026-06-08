package org.example.keibaapp;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RaceController {

    // ★重要：Springに「サービスを使ってね」と伝えるための変数とコンストラクタ
    private final RaceService raceService;

    public RaceController(RaceService raceService) {
        this.raceService = raceService;
    }

    @GetMapping("/races")
    public String showRaces(Model model) {
        System.out.println("★コントローラーにアクセスが来ました！"); // ←これを入れる
        model.addAttribute("races", raceService.getRaces());
        System.out.println("★Serviceからデータを受け取りました！"); // ←これを入れる
        return "raceList";
    }
}
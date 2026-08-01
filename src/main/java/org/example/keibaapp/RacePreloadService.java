package org.example.keibaapp;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class RacePreloadService {

    private final RaceService raceService;

    public RacePreloadService(RaceService raceService) {
        this.raceService = raceService;
    }

    // 曜日をハードコードせず毎日実行し、開催の有無自体はhasRaceToday()で判定する
    // (夏の変則開催等で土日以外に開催されるケースに対応するため)
    private boolean skipIfNoRaceToday(String label) {
        if (!raceService.hasRaceToday()) {
            System.out.println("【事前キャッシュ】本日は開催がないためスキップ(" + label + ")");
            return true;
        }
        return false;
    }

    // 8:00 に先読み（ユーザーが9時台にアクセスした時に即表示）
    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Tokyo")
    public void preloadMorningRaces() {
        if (skipIfNoRaceToday("8:00")) {
            return;
        }
        System.out.println("【事前キャッシュ】取得開始(8:00)");
        raceService.getRaces();
        System.out.println("【事前キャッシュ】完了(8:00)");
    }

    // 8:50 にキャッシュ更新（8:00+90分=9:30の期限切れ前にリフレッシュ）
    // これがないと9:30頃の1R通知でキャッシュミスが起きる
    @Scheduled(cron = "0 50 8 * * *", zone = "Asia/Tokyo")
    public void refreshEarlyMorningRaces() {
        if (skipIfNoRaceToday("8:50")) {
            return;
        }
        System.out.println("【キャッシュ更新】リフレッシュ(8:50)");
        raceService.getRaces();
        System.out.println("【キャッシュ更新】完了");
    }

    // 10:00 にキャッシュ更新（8:50+90分=10:20まで有効）
    @Scheduled(cron = "0 0 10 * * *", zone = "Asia/Tokyo")
    public void refreshMidMorningRaces() {
        if (skipIfNoRaceToday("10:00")) {
            return;
        }
        System.out.println("【キャッシュ更新】リフレッシュ(10:00)");
        raceService.getRaces();
        System.out.println("【キャッシュ更新】完了");
    }

    // 11:31 に昼帯のキャッシュを先読み
    @Scheduled(cron = "0 31 11 * * *", zone = "Asia/Tokyo")
    public void preloadMiddayRaces() {
        if (skipIfNoRaceToday("11:31")) {
            return;
        }
        System.out.println("【事前キャッシュ】取得開始(11:31)");
        raceService.getRaces();
        System.out.println("【事前キャッシュ】完了(11:31)");
    }

    // 14:01 に午後帯のキャッシュを先読み
    @Scheduled(cron = "0 1 14 * * *", zone = "Asia/Tokyo")
    public void preloadAfternoonRaces() {
        if (skipIfNoRaceToday("14:01")) {
            return;
        }
        System.out.println("【事前キャッシュ】取得開始(14:01)");
        raceService.getRaces();
        System.out.println("【事前キャッシュ】完了(14:01)");
    }
}

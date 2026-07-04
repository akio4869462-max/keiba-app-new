package org.example.keibaapp;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class RacePreloadService {

    private final RaceService raceService;

    public RacePreloadService(RaceService raceService) {
        this.raceService = raceService;
    }

    // 8:00 に1-4Rを先読み（ユーザーが9時台にアクセスした時に即表示）
    @Scheduled(cron = "0 0 8 * * SAT,SUN", zone = "Asia/Tokyo")
    public void preloadMorningRaces() {
        System.out.println("【事前キャッシュ】1-4R 取得開始");
        raceService.getRaces();
        System.out.println("【事前キャッシュ】1-4R 完了");
    }

    // 8:50 にキャッシュ更新（8:00+90分=9:30の期限切れ前にリフレッシュ）
    // これがないと9:30頃の1R通知でキャッシュミスが起きる
    @Scheduled(cron = "0 50 8 * * SAT,SUN", zone = "Asia/Tokyo")
    public void refreshEarlyMorningRaces() {
        System.out.println("【キャッシュ更新】1-4R リフレッシュ(8:50)");
        raceService.getRaces();
        System.out.println("【キャッシュ更新】完了");
    }

    // 10:00 にキャッシュ更新（2R〜4Rが10時台に発走するため、8:50+90分=10:20まで有効）
    @Scheduled(cron = "0 0 10 * * SAT,SUN", zone = "Asia/Tokyo")
    public void refreshMidMorningRaces() {
        System.out.println("【キャッシュ更新】1-4R リフレッシュ(10:00)");
        raceService.getRaces();
        System.out.println("【キャッシュ更新】完了");
    }

    // 11:31 に5-8Rを先読み（時間帯切り替え直後）
    @Scheduled(cron = "0 31 11 * * SAT,SUN", zone = "Asia/Tokyo")
    public void preloadMiddayRaces() {
        System.out.println("【事前キャッシュ】5-8R 取得開始");
        raceService.getRaces();
        System.out.println("【事前キャッシュ】5-8R 完了");
    }

    // 14:01 に9-12Rを先読み（時間帯切り替え直後）
    @Scheduled(cron = "0 1 14 * * SAT,SUN", zone = "Asia/Tokyo")
    public void preloadAfternoonRaces() {
        System.out.println("【事前キャッシュ】9-12R 取得開始");
        raceService.getRaces();
        System.out.println("【事前キャッシュ】9-12R 完了");
    }
}

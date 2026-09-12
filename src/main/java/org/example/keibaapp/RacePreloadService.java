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

    // 8時〜18時、毎時1分にキャッシュを更新する。
    // RaceNotificationService.checkFavorites()はキャッシュ(TTL 90分)のみを参照し
    // 自分ではフェッチしない設計のため、ここで定期的にキャッシュを温め続けないと、
    // しばらくアクセスがない間にキャッシュが失効し、その間の通知チェックが
    // 丸ごとスキップされてしまう(実際に発生した不具合)。
    // 固定時刻を飛び飛びに並べる方式だと更新間隔が90分を超える隙間が生まれうるため、
    // TTLより短い60分間隔で通知チェックの実行窓(8:00〜19:00)を隙間なくカバーする
    @Scheduled(cron = "0 1 8-18 * * *", zone = "Asia/Tokyo")
    public void refreshRaceCache() {
        if (skipIfNoRaceToday("定期リフレッシュ")) {
            return;
        }
        System.out.println("【キャッシュ更新】定期リフレッシュ開始");
        raceService.getRaces();
        System.out.println("【キャッシュ更新】完了");
    }
}

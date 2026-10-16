package org.example.keibaapp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class RaceNotificationService {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    // 通知チェックは毎分実行されるため、実際の通知は発走の1〜2分前に届く
    private static final int NOTIFY_MINUTES_BEFORE = 2;

    private final FavoriteHorseRepository horseRepository;
    private final FavoriteJockeyRepository jockeyRepository;
    private final RaceService raceService;
    private final DiscordNotificationService discordNotificationService;
    private final NotificationHistoryRepository historyRepository;
    private final NotificationJockeyHistoryRepository historyJockeyRepository;
    private final RaceCacheService raceCacheService;
    // 通知窓(発走の2分前〜発走)の判定が「今」を参照するための時計。
    // テストでは固定時計を渡す(実時計だと日付またぎ等で結果が変わるため)
    private final Clock clock;

    @Autowired
    public RaceNotificationService(
            FavoriteHorseRepository horseRepository,
            FavoriteJockeyRepository jockeyRepository,
            RaceService raceService,
            DiscordNotificationService discordNotificationService,
            NotificationHistoryRepository historyRepository,
            NotificationJockeyHistoryRepository historyJockeyRepository,
            RaceCacheService raceCacheService) {

        this(horseRepository, jockeyRepository, raceService, discordNotificationService,
                historyRepository, historyJockeyRepository, raceCacheService, Clock.system(JST));
    }

    RaceNotificationService(
            FavoriteHorseRepository horseRepository,
            FavoriteJockeyRepository jockeyRepository,
            RaceService raceService,
            DiscordNotificationService discordNotificationService,
            NotificationHistoryRepository historyRepository,
            NotificationJockeyHistoryRepository historyJockeyRepository,
            RaceCacheService raceCacheService,
            Clock clock) {

        this.horseRepository = horseRepository;
        this.jockeyRepository = jockeyRepository;
        this.raceService = raceService;
        this.discordNotificationService = discordNotificationService;
        this.historyRepository = historyRepository;
        this.historyJockeyRepository = historyJockeyRepository;
        this.raceCacheService = raceCacheService;
        this.clock = clock;
    }

    public void checkFavorites() {

        // キャッシュのみ使う（フェッチはPreloadServiceに任せる）
        // raceService.getRaces()はキャッシュミス時に30〜60秒かかるため、
        // 通知ウィンドウ（発走2分前）を超えてしまい通知が届かなくなる
        if (!raceCacheService.hasCachedRaces()) {
            System.out.println("レースキャッシュなし - 通知チェックをスキップ");
            return;
        }

        List<RaceInfo> races = raceCacheService.getCachedRaces();
        LocalDate today = LocalDate.now(clock);
        LocalTime now = LocalTime.now(clock);

        // 馬の通知と騎手の通知は互いに影響させない。通知窓が2分しか無いため、
        // 片方の処理で予期しない例外が出ても、もう片方の通知を取りこぼさないようにする
        try {
            notifyFavoriteHorses(races, today, now);
        } catch (Exception e) {
            System.out.println("お気に入り馬の通知チェックでエラー: " + e.getMessage());
        }

        try {
            notifyFavoriteJockeys(races, today, now);
        } catch (Exception e) {
            System.out.println("お気に入り騎手の通知チェックでエラー: " + e.getMessage());
        }
    }

    private boolean isInNotifyWindow(RaceInfo race, LocalTime now) {
        LocalTime notifyTime = race.getRaceTime().minusMinutes(NOTIFY_MINUTES_BEFORE);

        return !now.isBefore(notifyTime) && !now.isAfter(race.getRaceTime());
    }

    // 送信に失敗(Discord側の障害・レート制限等)しても、他の通知の処理を止めない。
    // 失敗した場合は通知済みとして記録しないので、通知窓の間は次の分に再試行される
    private boolean trySend(String message) {
        try {
            discordNotificationService.sendMessage(message);
            return true;
        } catch (Exception e) {
            System.out.println("Discord通知の送信に失敗: " + e.getMessage());
            return false;
        }
    }

    private void notifyFavoriteHorses(List<RaceInfo> races, LocalDate today, LocalTime now) {
        for (FavoriteHorse favorite : horseRepository.findAll()) {

            for (RaceInfo race : races) {

                if (!isInNotifyWindow(race, now)) {
                    continue;
                }

                for (Horse horse : race.getHorses()) {

                    boolean nameMatch = favorite.getHorseName().equals(horse.getName());
                    boolean sireMatch = !nameMatch && favorite.getHorseName().equals(horse.getSire());

                    if (!nameMatch && !sireMatch) {
                        continue;
                    }

                    // レース名("サラ系2歳未勝利"等)は同じ日に複数のレースで同名になるため、
                    // 開催場+レース番号で通知済みかを判定する
                    if (historyRepository.existsByHorseNameAndVenueAndRaceNumberAndRaceDate(
                            horse.getName(), race.getVenue(), race.getRaceNum(), today)) {

                        System.out.println("通知済みのためスキップ: " + horse.getName());
                        continue;
                    }

                    String message = nameMatch
                            ? "【出走通知】" + horse.getName()
                                    + "(" + horse.getUmaban() + "番)"
                                    + " が " + race.getVenue()
                                    + " " + race.getRaceNum()
                                    + "R に出走します！"
                            : "【出走通知】お気に入り馬「" + favorite.getHorseName() + "」の産駒 "
                                    + horse.getName() + "(" + horse.getUmaban() + "番)"
                                    + " が " + race.getVenue()
                                    + " " + race.getRaceNum()
                                    + "R に出走します！";

                    System.out.println(message);

                    if (trySend(message)) {
                        historyRepository.save(new NotificationHistory(
                                horse.getName(), race.getRaceName(), race.getVenue(), race.getRaceNum(), today));
                    }
                }
            }
        }
    }

    private void notifyFavoriteJockeys(List<RaceInfo> races, LocalDate today, LocalTime now) {
        for (FavoriteJockey favorite : jockeyRepository.findAll()) {

            for (RaceInfo race : races) {

                if (!isInNotifyWindow(race, now)) {
                    continue;
                }

                for (Horse horse : race.getHorses()) {

                    if (!favorite.getJockeyName().equals(horse.getJockeyName())) {
                        continue;
                    }

                    // 騎手は1日に何レースも騎乗し、同名の条件戦も多いため、
                    // レース名ではなく開催場+レース番号で通知済みかを判定する
                    if (historyJockeyRepository.existsByJockeyNameAndVenueAndRaceNumberAndRaceDate(
                            horse.getJockeyName(), race.getVenue(), race.getRaceNum(), today)) {

                        System.out.println("通知済みのためスキップ: " + horse.getJockeyName());
                        continue;
                    }

                    String message =
                            "【出走通知】" + horse.getJockeyName()
                                    + " が " + race.getVenue()
                                    + " " + race.getRaceNum()
                                    + "R で" + horse.getName()
                                    + "(" + horse.getUmaban() + "番)"
                                    + "に騎乗します！";

                    System.out.println(message);

                    if (trySend(message)) {
                        historyJockeyRepository.save(new NotificationJockeyHistory(
                                horse.getJockeyName(), race.getRaceName(), race.getVenue(), race.getRaceNum(), today));
                    }
                }
            }
        }
    }

    public void checkFavoritesWithDummy(List<RaceInfo> races) {
        for (FavoriteHorse favorite
                : horseRepository.findAll()) {

            for (RaceInfo race : races) {

                for (Horse horse : race.getHorses()) {

                    if (favorite.getHorseName()
                            .equals(horse.getName())) {

                        String message =
                                "【デバッグ通知】" + horse.getName()
                                        + "(" + horse.getUmaban() + "番)"
                                        + " が " + race.getVenue()
                                        + " " + race.getRaceNum()
                                        + "R に出走します！";

                        System.out.println(message);

                        discordNotificationService.sendMessage(message);
                    }
                }
            }
        }

        for (FavoriteJockey favorite
                : jockeyRepository.findAll()) {

            for (RaceInfo race : races) {

                for (Horse horse : race.getHorses()) {

                    if (favorite.getJockeyName()
                            .equals(horse.getJockeyName())) {

                        String message =
                                "【デバッグ通知】" + horse.getJockeyName()
                                        + " が " + race.getVenue()
                                        + " " + race.getRaceNum()
                                        + "R で" + horse.getName()
                                        + "(" + horse.getUmaban() + "番)"
                                        + "に騎乗します！";

                        System.out.println(message);

                        discordNotificationService.sendMessage(message);
                    }
                }
            }
        }
    }

    // 曜日をハードコードせず毎日実行する(夏の変則開催等、土日以外の開催に対応するため)。
    // 非開催日は基本的にgetRaces()がraceCacheServiceに書き込まないためキャッシュが
    // 存在しなくなるが、前回開催日のキャッシュが残っている場合でも
    // hasCachedRaces()が鮮度もチェックするため、古いキャッシュで
    // checkFavorites()が誤って動いてしまうことはない
    @Scheduled(cron = "0 * * * * *", zone = "Asia/Tokyo")
    public void scheduledCheck() {
        LocalTime time = LocalTime.now(clock);

        if (time.isAfter(LocalTime.of(8, 0)) && time.isBefore(LocalTime.of(19, 0))) {
            System.out.println("定期通知チェックを実行します");
            checkFavorites();
        }
    }
}
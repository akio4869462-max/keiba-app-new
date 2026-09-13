package org.example.keibaapp;

import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

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

    public RaceNotificationService(
            FavoriteHorseRepository horseRepository,
            FavoriteJockeyRepository jockeyRepository,
            RaceService raceService,
            DiscordNotificationService discordNotificationService,
            NotificationHistoryRepository historyRepository,
            NotificationJockeyHistoryRepository historyJockeyRepository,
            RaceCacheService raceCacheService) {

        this.horseRepository = horseRepository;
        this.jockeyRepository = jockeyRepository;
        this.raceService = raceService;
        this.discordNotificationService = discordNotificationService;
        this.historyRepository = historyRepository;
        this.historyJockeyRepository = historyJockeyRepository;
        this.raceCacheService = raceCacheService;
    }

    public void checkFavorites() {

        // キャッシュのみ使う（フェッチはPreloadServiceに任せる）
        // raceService.getRaces()はキャッシュミス時に30〜60秒かかるため、
        // 通知ウィンドウ（発走5分前）を超えてしまい通知が届かなくなる
        if (!raceCacheService.hasCachedRaces()) {
            System.out.println("レースキャッシュなし - 通知チェックをスキップ");
            return;
        }

        List<RaceInfo> races = raceCacheService.getCachedRaces();
        LocalDate today = LocalDate.now(JST);

        for (FavoriteHorse favorite
                : horseRepository.findAll()) {

            for (RaceInfo race : races) {

                for (Horse horse : race.getHorses()) {

                    boolean nameMatch = favorite.getHorseName().equals(horse.getName());
                    boolean sireMatch = !nameMatch && favorite.getHorseName().equals(horse.getSire());

                    if (nameMatch || sireMatch) {
                        LocalTime now = LocalTime.now(JST);
                        LocalTime notifyTime = race.getRaceTime().minusMinutes(NOTIFY_MINUTES_BEFORE);

                        if (now.isBefore(notifyTime) || now.isAfter(race.getRaceTime())) {
                            continue;
                        }

                        if (historyRepository
                                .findByHorseNameAndRaceNameAndRaceDate(
                                        horse.getName(),
                                        race.getRaceName(),
                                        today)
                                .isPresent()) {

                            System.out.println(
                                    "通知済みのためスキップ: "
                                            + horse.getName());

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

                        discordNotificationService.sendMessage(message);
                        historyRepository.save(new NotificationHistory(horse.getName(), race.getRaceName(), today));
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
                        LocalTime now = LocalTime.now(JST);
                        LocalTime notifyTime = race.getRaceTime().minusMinutes(NOTIFY_MINUTES_BEFORE);

                        if (now.isBefore(notifyTime) || now.isAfter(race.getRaceTime())) {
                            continue;
                        }

                        if (historyJockeyRepository
                                .findByJockeyNameAndRaceNameAndRaceDate(
                                        horse.getJockeyName(),
                                        race.getRaceName(),
                                        today)
                                .isPresent()) {

                            System.out.println(
                                    "通知済みのためスキップ: "
                                            + horse.getJockeyName());

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

                        discordNotificationService.sendMessage(message);
                        historyJockeyRepository.save(new NotificationJockeyHistory(horse.getJockeyName(), race.getRaceName(), today));
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
        LocalTime time = LocalTime.now(JST);

        if (time.isAfter(LocalTime.of(8, 0)) && time.isBefore(LocalTime.of(19, 0))) {
            System.out.println("定期通知チェックを実行します");
            checkFavorites();
        }
    }
}
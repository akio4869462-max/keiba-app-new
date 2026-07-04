package org.example.keibaapp;

import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class RaceNotificationService {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

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

        for (FavoriteHorse favorite
                : horseRepository.findAll()) {

            for (RaceInfo race : races) {

                for (Horse horse : race.getHorses()) {

                    if (favorite.getHorseName()
                            .equals(horse.getName())) {
                        LocalTime now = LocalTime.now(JST);
                        LocalTime notifyTime = race.getRaceTime().minusMinutes(30);

                        if (now.isBefore(notifyTime) || now.isAfter(race.getRaceTime())) {
                            continue;
                        }

                        if (historyRepository
                                .findByHorseNameAndRaceName(
                                        horse.getName(),
                                        race.getRaceName())
                                .isPresent()) {

                            System.out.println(
                                    "通知済みのためスキップ: "
                                            + horse.getName());

                            continue;
                        }

                        String message =
                                "【出走通知】" + horse.getName()
                                        + " が " + race.getVenue()
                                        + " " + race.getRaceNum()
                                        + "R に出走します！";

                        System.out.println(message);

                        discordNotificationService.sendMessage(message);
                        historyRepository.save(new NotificationHistory(horse.getName(), race.getRaceName()));
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
                        LocalTime notifyTime = race.getRaceTime().minusMinutes(30);

                        if (now.isBefore(notifyTime) || now.isAfter(race.getRaceTime())) {
                            continue;
                        }

                        if (historyJockeyRepository
                                .findByJockeyNameAndRaceName(
                                        horse.getJockeyName(),
                                        race.getRaceName())
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
                                        + "に騎乗します！";

                        System.out.println(message);

                        discordNotificationService.sendMessage(message);
                        historyJockeyRepository.save(new NotificationJockeyHistory(horse.getJockeyName(), race.getRaceName()));
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
                                        + "に騎乗します！";

                        System.out.println(message);

                        discordNotificationService.sendMessage(message);
                    }
                }
            }
        }
    }

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Tokyo")
    public void scheduledCheck() {
        DayOfWeek day = LocalDate.now(JST).getDayOfWeek();
        LocalTime time = LocalTime.now(JST);

        if ((day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY)
                && (time.isAfter(LocalTime.of(9, 0)) && time.isBefore(LocalTime.of(17, 0)))) {
            System.out.println("定期通知チェックを実行します");
            checkFavorites();
        }
    }
}
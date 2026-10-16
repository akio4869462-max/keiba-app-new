package org.example.keibaapp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

class RaceNotificationServiceTest {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 4);

    // 通知窓(発走2分前〜発走)の判定が実時計に依存しないよう、10:00(JST)に固定した時計を使う
    private final Clock clock = Clock.fixed(TODAY.atTime(10, 0).atZone(JST).toInstant(), JST);

    private FavoriteHorseRepository horseRepository;
    private FavoriteJockeyRepository jockeyRepository;
    private DiscordNotificationService discord;
    private NotificationHistoryRepository historyRepository;
    private NotificationJockeyHistoryRepository jockeyHistoryRepository;
    private RaceCacheService raceCacheService;
    private RaceNotificationService service;

    // 通知済みとして保存されたキー(DBの代わりにメモリ上で再現する)
    private final Set<String> horseHistory = new HashSet<>();
    private final Set<String> jockeyHistory = new HashSet<>();

    @BeforeEach
    void setUp() {
        horseRepository = mock(FavoriteHorseRepository.class);
        jockeyRepository = mock(FavoriteJockeyRepository.class);
        discord = mock(DiscordNotificationService.class);
        historyRepository = mock(NotificationHistoryRepository.class);
        jockeyHistoryRepository = mock(NotificationJockeyHistoryRepository.class);
        raceCacheService = new RaceCacheService();

        when(historyRepository.existsByHorseNameAndVenueAndRaceNumberAndRaceDate(anyString(), anyString(), anyInt(), any()))
                .thenAnswer(inv -> horseHistory.contains(
                        inv.getArgument(0) + "|" + inv.getArgument(1) + "|" + inv.getArgument(2)));
        when(historyRepository.save(any())).thenAnswer(inv -> {
            NotificationHistory h = inv.getArgument(0);
            horseHistory.add(h.getHorseName() + "|" + h.getVenue() + "|" + h.getRaceNumber());
            return h;
        });

        when(jockeyHistoryRepository.existsByJockeyNameAndVenueAndRaceNumberAndRaceDate(anyString(), anyString(), anyInt(), any()))
                .thenAnswer(inv -> jockeyHistory.contains(
                        inv.getArgument(0) + "|" + inv.getArgument(1) + "|" + inv.getArgument(2)));
        when(jockeyHistoryRepository.save(any())).thenAnswer(inv -> {
            NotificationJockeyHistory h = inv.getArgument(0);
            jockeyHistory.add(h.getJockeyName() + "|" + h.getVenue() + "|" + h.getRaceNumber());
            return h;
        });

        when(horseRepository.findAll()).thenReturn(List.of());
        when(jockeyRepository.findAll()).thenReturn(List.of());

        service = new RaceNotificationService(
                horseRepository, jockeyRepository, mock(RaceService.class), discord,
                historyRepository, jockeyHistoryRepository, raceCacheService, clock);
    }

    private Horse horse(String name, String jockey, int umaban) {
        return new Horse("1", String.valueOf(umaban), name, jockey, "57.0", 5.0);
    }

    private RaceInfo race(int raceNumber, String raceName, LocalTime time, Horse... horses) {
        return new RaceInfo(raceNumber, "東京", raceName, time, "芝", "1600m", List.of(horses));
    }

    private void cache(RaceInfo... races) {
        raceCacheService.cacheRaces(List.of(races));
    }

    @Test
    void checkFavorites_shouldNotifyJockeyForEachRaceEvenWhenRaceNamesAreIdentical() {
        // 同じ日に同名のレース("サラ系2歳未勝利"等)が複数あり、騎手は両方に騎乗する。
        // 通知済みの判定がレース名だけだと、2つ目のレースが「通知済み」と誤判定されて届かなかった
        when(jockeyRepository.findAll()).thenReturn(List.of(new FavoriteJockey("田辺 裕信")));
        cache(
                race(1, "サラ系2歳未勝利", LocalTime.of(10, 1), horse("馬A", "田辺 裕信", 3)),
                race(2, "サラ系2歳未勝利", LocalTime.of(10, 2), horse("馬B", "田辺 裕信", 7)));

        service.checkFavorites();

        verify(discord).sendMessage(contains("東京 1R で馬A"));
        verify(discord).sendMessage(contains("東京 2R で馬B"));
        verify(discord, times(2)).sendMessage(anyString());
    }

    @Test
    void checkFavorites_shouldNotifyBothHorseAndJockeyInSameRace() {
        when(horseRepository.findAll()).thenReturn(List.of(new FavoriteHorse("馬A")));
        when(jockeyRepository.findAll()).thenReturn(List.of(new FavoriteJockey("田辺 裕信")));
        cache(race(1, "サラ系2歳未勝利", LocalTime.of(10, 1), horse("馬A", "田辺 裕信", 3)));

        service.checkFavorites();

        verify(discord).sendMessage(contains("馬A(3番) が 東京 1R に出走します"));
        verify(discord).sendMessage(contains("田辺 裕信 が 東京 1R で馬A(3番)に騎乗します"));
    }

    @Test
    void checkFavorites_shouldStillNotifyJockeyWhenHorseNotificationFails() {
        // 馬の通知の送信が失敗(Discord側の障害等)しても、騎手の通知を取りこぼさない。
        // 通知窓が2分しか無いので、馬側の例外で騎手側の処理が丸ごと飛ぶのを防ぐ
        when(horseRepository.findAll()).thenReturn(List.of(new FavoriteHorse("馬A")));
        when(jockeyRepository.findAll()).thenReturn(List.of(new FavoriteJockey("田辺 裕信")));
        doThrow(new RuntimeException("429 Too Many Requests"))
                .when(discord).sendMessage(contains("に出走します"));
        cache(race(1, "サラ系2歳未勝利", LocalTime.of(10, 1), horse("馬A", "田辺 裕信", 3)));

        service.checkFavorites();

        verify(discord).sendMessage(contains("に騎乗します"));
        verify(historyRepository, never()).save(any());
        verify(jockeyHistoryRepository).save(any());
    }

    @Test
    void checkFavorites_shouldRetryNextMinuteWhenSendFailedAndNotRecordAsNotified() {
        when(jockeyRepository.findAll()).thenReturn(List.of(new FavoriteJockey("田辺 裕信")));
        doThrow(new RuntimeException("network error"))
                .doNothing()
                .when(discord).sendMessage(anyString());
        cache(race(1, "サラ系2歳未勝利", LocalTime.of(10, 1), horse("馬A", "田辺 裕信", 3)));

        service.checkFavorites();   // 1回目: 失敗(通知済みにはしない)
        service.checkFavorites();   // 次の分: 再試行して成功

        verify(discord, times(2)).sendMessage(anyString());
        verify(jockeyHistoryRepository, times(1)).save(any());
    }

    @Test
    void checkFavorites_shouldNotifyOnlyOncePerRace() {
        when(jockeyRepository.findAll()).thenReturn(List.of(new FavoriteJockey("田辺 裕信")));
        cache(race(1, "サラ系2歳未勝利", LocalTime.of(10, 1), horse("馬A", "田辺 裕信", 3)));

        service.checkFavorites();
        service.checkFavorites();

        verify(discord, times(1)).sendMessage(anyString());
    }

    @Test
    void checkFavorites_shouldNotNotifyOutsideNotifyWindow() {
        when(jockeyRepository.findAll()).thenReturn(List.of(new FavoriteJockey("田辺 裕信")));
        cache(
                race(1, "サラ系2歳未勝利", LocalTime.of(10, 30), horse("馬A", "田辺 裕信", 3)),   // まだ先
                race(2, "サラ系2歳未勝利", LocalTime.of(9, 59), horse("馬B", "田辺 裕信", 7)));   // 発走済み

        service.checkFavorites();

        verify(discord, never()).sendMessage(anyString());
    }

    @Test
    void checkFavorites_shouldSkipWhenNoCachedRaces() {
        when(jockeyRepository.findAll()).thenReturn(List.of(new FavoriteJockey("田辺 裕信")));

        service.checkFavorites();

        verify(discord, never()).sendMessage(anyString());
    }

    @Test
    void checkFavorites_shouldMatchSireForOffspringNotification() {
        when(horseRepository.findAll()).thenReturn(List.of(new FavoriteHorse("キズナ")));
        Horse offspring = horse("馬A", "田辺 裕信", 3);
        offspring.setSire("キズナ");
        cache(race(1, "サラ系2歳未勝利", LocalTime.of(10, 1), offspring));

        service.checkFavorites();

        verify(discord).sendMessage(contains("お気に入り馬「キズナ」の産駒 馬A(3番)"));
    }
}

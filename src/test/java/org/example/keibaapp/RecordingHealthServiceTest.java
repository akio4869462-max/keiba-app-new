package org.example.keibaapp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RecordingHealthServiceTest {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 1);

    private OddsSnapshotRepository snapshotRepository;
    private MorningOddsRepository morningOddsRepository;
    private MorningOddsService morningOddsService;
    private DiscordNotificationService discord;
    private RaceCacheService cache;
    private RecordingHealthService service;

    private final List<Object[]> morning = new ArrayList<>();
    private final List<Object[]> tenMinute = new ArrayList<>();
    private final List<Object[]> close = new ArrayList<>();
    private int expectedFromService = 0;

    @BeforeEach
    void setUp() {
        snapshotRepository = mock(OddsSnapshotRepository.class);
        morningOddsRepository = mock(MorningOddsRepository.class);
        morningOddsService = mock(MorningOddsService.class);
        discord = mock(DiscordNotificationService.class);
        cache = new RaceCacheService();

        when(morningOddsRepository.findRaceKeys(DAY)).thenAnswer(inv -> morning);
        when(morningOddsRepository.findTopByOrderByCapturedAtDesc()).thenReturn(Optional.empty());
        when(snapshotRepository.findTenMinuteRaceKeys(DAY)).thenAnswer(inv -> tenMinute);
        when(snapshotRepository.findCloseRaceKeys(DAY)).thenAnswer(inv -> close);
        when(snapshotRepository.findTopByOrderByCapturedAtDesc()).thenReturn(Optional.empty());
        when(morningOddsService.getTodayRaceCount(DAY)).thenAnswer(inv -> expectedFromService);

        service = new RecordingHealthService(snapshotRepository, morningOddsRepository, cache, morningOddsService, discord,
                Clock.fixed(DAY.atTime(12, 0).atZone(JST).toInstant(), JST));
    }

    private LocalDateTime at(int hour, int minute) {
        return DAY.atTime(hour, minute);
    }

    private RaceInfo race(String venue, int number, LocalTime time) {
        return new RaceInfo(number, venue, "テスト", time, "芝", "1600m", List.of());
    }

    private Object[] key(String venue, int number) {
        return new Object[]{venue, number};
    }

    @Test
    void findProblems_shouldBeEmptyWhenNotARaceDay() {
        // 開催のレース数が分からない(0)・朝オッズの記録も無い日は、何も問題にしない
        assertTrue(service.findProblems(at(12, 0)).isEmpty());
    }

    @Test
    void findProblems_shouldFlagIncompleteMorningOddsOnlyAfterTheCheckTime() {
        expectedFromService = 36;
        for (int i = 1; i <= 30; i++) {
            morning.add(key("東京", i));
        }
        tenMinute.add(key("東京", 1));

        assertTrue(service.findProblems(at(9, 0)).isEmpty());
        List<String> after = service.findProblems(at(9, 30));
        assertEquals(1, after.size());
        assertTrue(after.get(0).contains("30 / 36"), after.get(0));
    }

    @Test
    void findProblems_shouldListRacesWithoutTenMinuteOrCloseRecordsAfterGracePeriod() {
        expectedFromService = 3;
        morning.addAll(List.of(key("東京", 1), key("東京", 2), key("新潟", 1)));
        tenMinute.add(key("東京", 1));
        close.add(key("東京", 1));
        cache.cacheRaces(List.of(
                race("東京", 1, LocalTime.of(9, 40)),     // 記録済み
                race("東京", 2, LocalTime.of(10, 10)),    // 10:50時点で発走40分後なのに記録なし
                race("新潟", 1, LocalTime.of(10, 45)),    // 発走5分後: まだ猶予内
                race("東京", 3, LocalTime.of(11, 30))));  // まだ先

        List<String> problems = service.findProblems(at(10, 50));

        assertEquals(2, problems.size(), problems.toString());
        assertTrue(problems.get(0).contains("10分前の記録が無いレース: 東京2R"), problems.get(0));
        assertFalse(problems.get(0).contains("新潟1R"));
        assertTrue(problems.get(1).contains("3分前の記録が無いレース: 東京2R"), problems.get(1));
    }

    @Test
    void findProblems_shouldFlagWhenNoTenMinuteRecordAtAllLateInTheMorning() {
        expectedFromService = 2;
        morning.addAll(List.of(key("東京", 1), key("東京", 2)));

        List<String> problems = service.findProblems(at(10, 30));

        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("10分前の記録が1件もありません"), problems.get(0));
    }

    @Test
    void findProblems_shouldIgnoreLeadingSpaceInOldVenueNames() {
        expectedFromService = 1;
        morning.add(key("東京", 1));
        tenMinute.add(key(" 東京", 1));      // 旧データ(開催場名の先頭に空白)
        close.add(key(" 東京", 1));
        cache.cacheRaces(List.of(race("東京", 1, LocalTime.of(9, 40))));

        assertTrue(service.findProblems(at(10, 40)).isEmpty());
    }

    @Test
    void check_shouldAlertOnceForTheSameProblemAndAgainWhenItChanges() {
        expectedFromService = 3;
        morning.add(key("東京", 1));
        tenMinute.add(key("東京", 1));
        close.add(key("東京", 1));

        service.check(at(9, 35));
        service.check(at(10, 5));          // 同じ内容
        verify(discord, times(1)).sendMessage(anyString());

        morning.add(key("東京", 2));       // 内容が変わった(2/3)
        service.check(at(10, 35));
        verify(discord, times(2)).sendMessage(anyString());
    }

    @Test
    void check_shouldAlertAgainAfterRecoveryAndNotWhenHealthy() {
        expectedFromService = 1;
        tenMinute.add(key("東京", 1));
        close.add(key("東京", 1));

        service.check(at(9, 35));          // 朝オッズ 0/1 → 通知
        morning.add(key("東京", 1));
        service.check(at(10, 5));          // 解消 → 通知しない
        morning.clear();
        service.check(at(10, 35));         // また発生 → 再通知

        verify(discord, times(2)).sendMessage(anyString());
    }

    @Test
    void check_shouldRetryNextTimeWhenDiscordFails() {
        expectedFromService = 2;
        doThrow(new RuntimeException("429")).doNothing().when(discord).sendMessage(anyString());

        service.check(at(9, 35));
        service.check(at(10, 5));

        verify(discord, times(2)).sendMessage(anyString());
    }

    @Test
    void summarize_shouldCountDistinctRacesAndUseTheLargerExpectedCount() {
        expectedFromService = 5;
        morning.addAll(List.of(key("東京", 1), key("東京", 2)));
        tenMinute.add(key("東京", 1));
        close.add(key("東京", 1));
        when(snapshotRepository.findTopByOrderByCapturedAtDesc()).thenReturn(Optional.empty());

        RecordingHealthService.Summary summary = service.summarize();

        assertEquals(5, summary.expectedRaces());
        assertEquals(2, summary.morningRaces());
        assertEquals(1, summary.tenMinuteRaces());
        assertEquals(1, summary.closeRaces());
        assertNull(summary.lastSnapshotAt());
    }
}

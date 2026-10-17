package org.example.keibaapp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MorningOddsServiceTest {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
    private static final LocalDate DAY = LocalDate.of(2026, 10, 18);
    private static final LocalDateTime NOW = DAY.atTime(8, 35);

    private MorningOddsRepository repository;
    private final List<MorningOdds> saved = new ArrayList<>();
    private final Map<String, Map<Integer, Double>> oddsByUrl = new HashMap<>();
    private final List<String> fetched = new ArrayList<>();
    private final List<String> failing = new ArrayList<>();
    private MorningOddsService service;

    @BeforeEach
    void setUp() {
        repository = mock(MorningOddsRepository.class);
        when(repository.existsByRaceDateAndVenueAndRaceNumber(any(), anyString(), anyInt())).thenAnswer(inv ->
                saved.stream().anyMatch(m -> m.getRaceDate().equals(inv.getArgument(0))
                        && m.getVenue().equals(inv.getArgument(1))
                        && m.getRaceNumber().equals(inv.getArgument(2))));
        when(repository.saveAll(any())).thenAnswer(inv -> {
            Iterable<MorningOdds> rows = inv.getArgument(0);
            rows.forEach(saved::add);
            return List.copyOf(saved);
        });

        service = new MorningOddsService(repository, new RaceParserService(), url -> {
            fetched.add(url);
            if (failing.contains(url)) {
                throw new IOException("network error");
            }
            return oddsByUrl.getOrDefault(url, Map.of());
        }, Clock.fixed(NOW.atZone(JST).toInstant(), JST));
    }

    private MorningOddsService.RaceTarget target(String venue, int raceNumber, LocalTime raceTime) {
        String url = "https://sports.yahoo.co.jp/keiba/race/odds/tfw/" + venue + raceNumber;
        oddsByUrl.put(url, Map.of(1, 3.5, 2, 12.0));
        return new MorningOddsService.RaceTarget(venue, raceNumber, raceTime, url);
    }

    @Test
    void capture_shouldSaveEveryHorseWithMinutesToPost() {
        int count = service.capture(List.of(target("新潟", 1, LocalTime.of(9, 50))), NOW);

        assertEquals(1, count);
        assertEquals(2, saved.size());
        MorningOdds first = saved.stream().filter(m -> m.getUmaban() == 1).findFirst().orElseThrow();
        assertEquals(3.5, first.getWinOdds());
        assertEquals(DAY, first.getRaceDate());
        assertEquals("新潟", first.getVenue());
        assertEquals(75, first.getMinutesToPost());   // 8:35 → 9:50
    }

    @Test
    void capture_shouldSkipRacesAlreadyRecorded() {
        MorningOddsService.RaceTarget race1 = target("新潟", 1, LocalTime.of(9, 50));
        service.capture(List.of(race1), NOW);
        fetched.clear();

        int count = service.capture(List.of(race1, target("新潟", 2, LocalTime.of(10, 20))), NOW.plusMinutes(10));

        assertEquals(1, count);
        assertEquals(1, fetched.size());   // 記録済みの1Rは取得しない
    }

    @Test
    void capture_shouldRetryFailedRacesAndStopOnlyWhenAllDone() {
        MorningOddsService.RaceTarget race1 = target("東京", 1, LocalTime.of(9, 50));
        MorningOddsService.RaceTarget race2 = target("東京", 2, LocalTime.of(10, 20));
        failing.add(race2.oddsUrl());

        assertEquals(1, service.capture(List.of(race1, race2), NOW));

        // 失敗があった日は終了扱いにならず、次の回で再試行される
        failing.clear();
        fetched.clear();
        assertEquals(1, service.capture(List.of(race1, race2), NOW.plusMinutes(10)));
        assertEquals(List.of(race2.oddsUrl()), fetched);
    }

    @Test
    void capture_shouldNotSaveRaceWhenOddsPageHasNoOdds() {
        MorningOddsService.RaceTarget race = new MorningOddsService.RaceTarget(
                "京都", 3, LocalTime.of(10, 50), "https://sports.yahoo.co.jp/keiba/race/odds/tfw/empty");

        assertEquals(0, service.capture(List.of(race), NOW));
        assertTrue(saved.isEmpty());
    }

    @Test
    void capture_shouldDoNothingWhenThereAreNoTargets() {
        assertEquals(0, service.capture(List.of(), NOW));
        verify(repository, never()).saveAll(any());
    }

    @Test
    void oddsSnapshot_shouldAttachMorningOddsAndMinutes() {
        OddsSnapshotRepository snapshotRepository = mock(OddsSnapshotRepository.class);
        MorningOddsRepository morningRepository = mock(MorningOddsRepository.class);
        when(morningRepository.findByRaceDateAndVenueAndRaceNumber(DAY, "東京", 11)).thenReturn(List.of(
                new MorningOdds(DAY, "東京", 11, 2, 9.0, 405, NOW)));
        OddsSnapshotService snapshotService = new OddsSnapshotService(snapshotRepository, morningRepository,
                Clock.fixed(DAY.atTime(15, 30).atZone(JST).toInstant(), JST));

        Horse withMorning = new Horse("2", "2", "馬2", "騎手", "57.0", 6.0);
        Horse withoutMorning = new Horse("3", "3", "馬3", "騎手", "57.0", 8.0);
        for (Horse h : List.of(withMorning, withoutMorning)) {
            h.setPopularity(2);
        }

        snapshotService.record(new RaceInfo(11, "東京", "テストS", LocalTime.of(15, 40), "芝", "1600m", List.of()),
                List.of(), List.of(withMorning, withoutMorning));

        org.mockito.ArgumentCaptor<List<OddsSnapshot>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(snapshotRepository).saveAll(captor.capture());
        OddsSnapshot a = captor.getValue().get(0);
        OddsSnapshot b = captor.getValue().get(1);
        assertEquals(9.0, a.getMorningWinOdds());
        assertEquals(405, a.getMorningMinutesToPost());
        assertNull(b.getMorningWinOdds());
        assertNull(b.getMorningMinutesToPost());
    }
}

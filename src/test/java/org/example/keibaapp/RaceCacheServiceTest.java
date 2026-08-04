package org.example.keibaapp;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RaceCacheServiceTest {

    @Test
    void putAndGetHorseDetail() {
        RaceCacheService cacheService = new RaceCacheService();

        HorseDetailInfo detail = HorseDetailInfo.empty();

        cacheService.putHorseDetail("test", detail);

        HorseDetailInfo result =
                cacheService.getHorseDetail("test");

        assertEquals(detail, result);
    }

    @Test
    void getHorseDetail_shouldReturnNullWhenNotExists() {
        RaceCacheService cacheService = new RaceCacheService();

        HorseDetailInfo result =
                cacheService.getHorseDetail("unknown");

        assertNull(result);
    }

    @Test
    void cacheRaces_shouldStoreAndReturnRaces() {
        RaceCacheService cacheService = new RaceCacheService();

        RaceInfo race = new RaceInfo(
                11,
                "東京",
                "テストレース",
                LocalTime.of(15, 40),
                "芝",
                "2000m",
                List.of()
        );

        List<RaceInfo> races = List.of(race);

        cacheService.cacheRaces("9-12", races);

        assertTrue(cacheService.isRaceCacheValid("9-12"));
        assertEquals(races, cacheService.getCachedRaces());
    }

    @Test
    void isRaceCacheValid_shouldReturnFalseWhenRangeIsDifferent() {
        RaceCacheService cacheService = new RaceCacheService();

        RaceInfo race = new RaceInfo(
                11,
                "東京",
                "テストレース",
                LocalTime.of(15, 40),
                "芝",
                "2000m",
                List.of()
        );

        cacheService.cacheRaces("9-12", List.of(race));

        assertFalse(cacheService.isRaceCacheValid("5-8"));
    }

    @Test
    void isRaceCacheValid_shouldReturnFalseWhenCacheIsEmpty() {
        RaceCacheService cacheService = new RaceCacheService();

        assertFalse(cacheService.isRaceCacheValid("9-12"));
        assertNull(cacheService.getCachedRaces());
    }

    @Test
    void hasCachedRaces_shouldReturnTrueForFreshCache() {
        RaceCacheService cacheService = new RaceCacheService();

        RaceInfo race = new RaceInfo(
                11, "東京", "テストレース", LocalTime.of(15, 40), "芝", "2000m", List.of());

        cacheService.cacheRaces("9-12", List.of(race));

        assertTrue(cacheService.hasCachedRaces());
    }

    @Test
    void hasCachedRaces_shouldReturnFalseWhenNothingCached() {
        RaceCacheService cacheService = new RaceCacheService();

        assertFalse(cacheService.hasCachedRaces());
    }

    @Test
    void hasCachedRaces_shouldReturnFalseForStaleCacheFromPreviousRaceDay() {
        // 曜日をハードコードせず毎日通知チェックが走るようになったため、
        // 前回開催日の古いキャッシュが残っていても誤って使わないことを確認する
        RaceCacheService cacheService = new RaceCacheService();

        RaceInfo race = new RaceInfo(
                11, "東京", "テストレース", LocalTime.of(15, 40), "芝", "2000m", List.of());

        cacheService.cacheRaces("9-12", List.of(race));
        cacheService.setLastFetchedAtForTesting(LocalDateTime.now().minusDays(1));

        assertFalse(cacheService.hasCachedRaces());
    }
}
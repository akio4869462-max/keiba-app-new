package org.example.keibaapp;

import org.junit.jupiter.api.Test;

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
}
package org.example.keibaapp;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
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

        cacheService.cacheRaces(races);

        assertTrue(cacheService.isRaceCacheValid());
        assertEquals(races, cacheService.getCachedRaces());
    }

    @Test
    void isRaceCacheValid_shouldReturnFalseWhenCacheIsEmpty() {
        RaceCacheService cacheService = new RaceCacheService();

        assertFalse(cacheService.isRaceCacheValid());
        assertNull(cacheService.getCachedRaces());
    }

    @Test
    void isRaceCacheValid_shouldReturnFalseWhenStale() {
        // :00/:30を跨いだだけでは無効にならず、TTL(90分)経過のみで無効になることを確認する
        RaceCacheService cacheService = new RaceCacheService();

        RaceInfo race = new RaceInfo(
                11, "東京", "テストレース", LocalTime.of(15, 40), "芝", "2000m", List.of());

        cacheService.cacheRaces(List.of(race));
        cacheService.setLastFetchedAtForTesting(LocalDateTime.now().minusMinutes(91));

        assertFalse(cacheService.isRaceCacheValid());
    }

    @Test
    void hasCachedRaces_shouldReturnTrueForFreshCache() {
        RaceCacheService cacheService = new RaceCacheService();

        RaceInfo race = new RaceInfo(
                11, "東京", "テストレース", LocalTime.of(15, 40), "芝", "2000m", List.of());

        cacheService.cacheRaces(List.of(race));

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

        cacheService.cacheRaces(List.of(race));
        cacheService.setLastFetchedAtForTesting(LocalDateTime.now().minusDays(1));

        assertFalse(cacheService.hasCachedRaces());
    }

    @Test
    void cacheBasicRaces_shouldStoreAndReturnRacesIndependentlyFromMainCache() {
        RaceCacheService cacheService = new RaceCacheService();

        RaceInfo basicRace = new RaceInfo(
                1, "東京", "出馬表用", LocalTime.of(10, 0), "芝", "1600m", List.of());
        RaceInfo fullRace = new RaceInfo(
                2, "東京", "予想用", LocalTime.of(10, 30), "ダ", "1400m", List.of());

        cacheService.cacheBasicRaces("10:0", List.of(basicRace));
        cacheService.cacheRaces(List.of(fullRace));

        assertTrue(cacheService.isBasicRaceCacheValid("10:0"));
        assertEquals(List.of(basicRace), cacheService.getCachedBasicRaces());

        // /races用と/predict用のキャッシュは別物として保持される
        assertEquals(List.of(fullRace), cacheService.getCachedRaces());
    }

    @Test
    void isBasicRaceCacheValid_shouldReturnFalseWhenRangeIsDifferent() {
        RaceCacheService cacheService = new RaceCacheService();

        RaceInfo race = new RaceInfo(
                1, "東京", "テストレース", LocalTime.of(10, 0), "芝", "1600m", List.of());

        cacheService.cacheBasicRaces("10:0", List.of(race));

        assertFalse(cacheService.isBasicRaceCacheValid("10:30"));
    }

    @Test
    void isBasicRaceCacheValid_shouldReturnFalseWhenCacheIsEmpty() {
        RaceCacheService cacheService = new RaceCacheService();

        assertFalse(cacheService.isBasicRaceCacheValid("10:0"));
        assertNull(cacheService.getCachedBasicRaces());
    }

    @Test
    void cacheFinishedRace_shouldStoreAndReturnRace() {
        RaceCacheService cacheService = new RaceCacheService();

        RaceInfo race = new RaceInfo(
                11, "東京", "テストレース", LocalTime.of(15, 40), "芝", "2000m", List.of());

        cacheService.cacheFinishedRace("東京", 11, race);

        assertEquals(race, cacheService.getFinishedRace("東京", 11));
    }

    @Test
    void getFinishedRace_shouldReturnNullWhenNotCached() {
        RaceCacheService cacheService = new RaceCacheService();

        assertNull(cacheService.getFinishedRace("東京", 11));
    }

    @Test
    void getFinishedRace_shouldDistinguishByVenueAndRaceNumber() {
        RaceCacheService cacheService = new RaceCacheService();

        RaceInfo tokyoRace = new RaceInfo(
                11, "東京", "東京11R", LocalTime.of(15, 40), "芝", "2000m", List.of());
        RaceInfo hanshinRace = new RaceInfo(
                11, "阪神", "阪神11R", LocalTime.of(15, 45), "ダ", "1800m", List.of());

        cacheService.cacheFinishedRace("東京", 11, tokyoRace);
        cacheService.cacheFinishedRace("阪神", 11, hanshinRace);

        assertEquals(tokyoRace, cacheService.getFinishedRace("東京", 11));
        assertEquals(hanshinRace, cacheService.getFinishedRace("阪神", 11));
    }

    @Test
    void getFinishedRace_shouldReturnNullAfterDayRollover() {
        // 発走済みレースのキャッシュはキーに日付を含まないため、
        // 日付が変わったら前日分を使わずクリアされることを確認する
        RaceCacheService cacheService = new RaceCacheService();

        RaceInfo race = new RaceInfo(
                11, "東京", "テストレース", LocalTime.of(15, 40), "芝", "2000m", List.of());

        cacheService.cacheFinishedRace("東京", 11, race);
        cacheService.setFinishedRaceCacheDateForTesting(LocalDate.now().minusDays(1));

        assertNull(cacheService.getFinishedRace("東京", 11));
    }

    @Test
    void wasOddsRefreshed_shouldReturnFalseUntilMarked() {
        RaceCacheService cacheService = new RaceCacheService();

        assertFalse(cacheService.wasOddsRefreshed("https://example.com/race/denma/1"));

        cacheService.markOddsRefreshed("https://example.com/race/denma/1");

        assertTrue(cacheService.wasOddsRefreshed("https://example.com/race/denma/1"));
    }

    @Test
    void wasOddsRefreshed_shouldDistinguishDifferentUrls() {
        RaceCacheService cacheService = new RaceCacheService();

        cacheService.markOddsRefreshed("https://example.com/race/denma/1");

        assertFalse(cacheService.wasOddsRefreshed("https://example.com/race/denma/2"));
    }

    @Test
    void wasOddsRefreshed_shouldResetAfterDayRollover() {
        RaceCacheService cacheService = new RaceCacheService();

        cacheService.markOddsRefreshed("https://example.com/race/denma/1");
        cacheService.setOddsRefreshedDateForTesting(LocalDate.now().minusDays(1));

        assertFalse(cacheService.wasOddsRefreshed("https://example.com/race/denma/1"));
    }
}
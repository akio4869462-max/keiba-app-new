package org.example.keibaapp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RaceParserServiceTest {

    private final RaceParserService service =
            new RaceParserService();

    @Test
    void getRaceNumber_shouldReturnRaceNumber() {
        int raceNumber = service.getRaceNumber(
                "https://sports.yahoo.co.jp/keiba/race/denma/202606200511"
        );

        assertEquals(11, raceNumber);
    }

    @Test
    void getRaceNumber_shouldHandleTrailingSlash() {
        int raceNumber = service.getRaceNumber(
                "https://sports.yahoo.co.jp/keiba/race/denma/202606200508/"
        );

        assertEquals(8, raceNumber);
    }

    @Test
    void shouldFetchRace_shouldReturnTrueWhenInRange() {
        boolean result = service.shouldFetchRace(
                "https://sports.yahoo.co.jp/keiba/race/denma/202606200511",
                9,
                12
        );

        assertTrue(result);
    }

    @Test
    void shouldFetchRace_shouldReturnFalseWhenOutOfRange() {
        boolean result = service.shouldFetchRace(
                "https://sports.yahoo.co.jp/keiba/race/denma/202606200505",
                9,
                12
        );

        assertFalse(result);
    }
}
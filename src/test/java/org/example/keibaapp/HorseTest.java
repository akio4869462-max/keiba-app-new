package org.example.keibaapp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HorseTest {

    @Test
    void getNetkeibaHorseUrl_shouldConvertYahooUrlToNetkeibaUrl() {
        Horse horse = new Horse("1", "1", "テスト馬", "テスト騎手", "57.0", 5.0);
        horse.setHorseUrl("https://sports.yahoo.co.jp/keiba/directory/horse/2020104616/");

        assertEquals("https://db.netkeiba.com/horse/2020104616/", horse.getNetkeibaHorseUrl());
    }

    @Test
    void getNetkeibaJockeyUrl_shouldConvertYahooUrlToNetkeibaUrl() {
        Horse horse = new Horse("1", "1", "テスト馬", "テスト騎手", "57.0", 5.0);
        horse.setJockeyUrl("https://sports.yahoo.co.jp/keiba/directory/jockey/01091/");

        assertEquals("https://db.netkeiba.com/jockey/01091/", horse.getNetkeibaJockeyUrl());
    }

    @Test
    void getNetkeibaHorseUrl_shouldHandleUrlWithoutTrailingSlash() {
        Horse horse = new Horse("1", "1", "テスト馬", "テスト騎手", "57.0", 5.0);
        horse.setHorseUrl("https://sports.yahoo.co.jp/keiba/directory/horse/2020104616");

        assertEquals("https://db.netkeiba.com/horse/2020104616/", horse.getNetkeibaHorseUrl());
    }

    @Test
    void getNetkeibaHorseUrl_shouldReturnEmptyWhenHorseUrlMissing() {
        Horse horse = new Horse("1", "1", "テスト馬", "テスト騎手", "57.0", 5.0);

        assertEquals("", horse.getNetkeibaHorseUrl());
    }
}

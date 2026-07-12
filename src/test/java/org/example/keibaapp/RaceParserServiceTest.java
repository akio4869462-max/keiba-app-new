package org.example.keibaapp;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
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

    @Test
    void createHorse_shouldParsePedigreeWithMixedLinksAndPlainText() {
        // 父・母父はリンク有り、母はプレーンテキスト（既存パターンに準拠）
        String html = "<table><tr>"
                + "<td>1</td>"
                + "<td>2</td>"
                + "<td><a href=\"/keiba/directory/horse/1/\">コントラポスト</a></td>"
                + "<td><a href=\"/keiba/directory/jockey/1/\">菊沢 一樹</a> 56.0</td>"
                + "<td>菊沢 隆徳</td>"
                + "<td class=\"hr-table__data--horsePedigree\">"
                + "<p>父：<a href=\"/keiba/directory/horse/2/\">ルーラーシップ</a></p>"
                + "<p>母：アカンサス</p>"
                + "<p>(母父：<a href=\"/keiba/directory/horse/3/\">フジキセキ</a>)</p>"
                + "</td>"
                + "<td>466(-4)</td>"
                + "<td>8(15.1)</td>"
                + "</tr></table>";

        Elements tds = Jsoup.parse(html).selectFirst("tr").select("td");
        Horse horse = service.createHorse(tds);

        assertEquals("ルーラーシップ", horse.getSire());
        assertEquals("アカンサス", horse.getDam());
        assertEquals("フジキセキ", horse.getDamSire());
    }

    @Test
    void createHorse_shouldLeavePedigreeNullWhenCellHasNoRecognizedFormat() {
        String html = "<table><tr>"
                + "<td>1</td>"
                + "<td>2</td>"
                + "<td><a href=\"/keiba/directory/horse/1/\">コントラポスト</a></td>"
                + "<td><a href=\"/keiba/directory/jockey/1/\">菊沢 一樹</a> 56.0</td>"
                + "<td>菊沢 隆徳</td>"
                + "<td class=\"hr-table__data--horsePedigree\"></td>"
                + "<td>466(-4)</td>"
                + "<td>8(15.1)</td>"
                + "</tr></table>";

        Elements tds = Jsoup.parse(html).selectFirst("tr").select("td");
        Horse horse = service.createHorse(tds);

        assertNull(horse.getSire());
        assertNull(horse.getDam());
        assertNull(horse.getDamSire());
    }
}
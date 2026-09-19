package org.example.keibaapp;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

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

    @Test
    void isRaceTimeRelevant_shouldReturnTrueForRaceStartingSoon() {
        LocalTime soon = LocalTime.now(ZoneId.of("Asia/Tokyo")).plusMinutes(30);

        assertTrue(service.isRaceTimeRelevant(soon));
    }

    @Test
    void isRaceTimeRelevant_shouldReturnTrueForRaceThatJustFinished() {
        LocalTime justPast = LocalTime.now(ZoneId.of("Asia/Tokyo")).minusMinutes(30);

        assertTrue(service.isRaceTimeRelevant(justPast));
    }

    @Test
    void isRaceTimeRelevant_shouldReturnFalseForRaceFarInTheFuture() {
        LocalTime farFuture = LocalTime.now(ZoneId.of("Asia/Tokyo")).plusMinutes(200);

        assertFalse(service.isRaceTimeRelevant(farFuture));
    }

    @Test
    void isRaceTimeRelevant_shouldReturnFalseForRaceLongPast() {
        LocalTime longPast = LocalTime.now(ZoneId.of("Asia/Tokyo")).minusMinutes(90);

        assertFalse(service.isRaceTimeRelevant(longPast));
    }

    @Test
    void isRaceTimeRelevant_shouldReturnFalseForNull() {
        assertFalse(service.isRaceTimeRelevant(null));
    }

    @Test
    void getRaceSchedules_shouldParseRaceNumberTimeAndUrlFromListPage() {
        // 実際の開催場一覧ページの構造(1レース=2行、日付/リンクセルはrowspan=2で
        // 最初の行にのみ存在する)を再現したスニペット
        String html = "<table><tbody>"
                + "<tr class=\"hr-tableSchedule__oddline\">"
                + "  <td class=\"hr-tableSchedule__data hr-tableSchedule__data--date\" rowspan=\"2\">1R<p>9:50</p></td>"
                + "  <td class=\"hr-tableSchedule__data\" rowspan=\"2\">"
                + "    <a class=\"hr-tableSchedule__link\" href=\"https://sports.yahoo.co.jp/keiba/race/index/2601020501\">"
                + "      <span class=\"hr-tableSchedule__title\">サラ系2歳未勝利</span>"
                + "    </a>"
                + "  </td>"
                + "  <td>1着</td><td>ロジアコース</td>"
                + "</tr>"
                + "<tr class=\"hr-tableSchedule__oddline\"><td>2着</td><td>サトノフルーク</td></tr>"
                + "<tr class=\"hr-tableSchedule__evenline\">"
                + "  <td class=\"hr-tableSchedule__data hr-tableSchedule__data--date\" rowspan=\"2\">2R<p>10:20</p></td>"
                + "  <td class=\"hr-tableSchedule__data\" rowspan=\"2\">"
                + "    <a class=\"hr-tableSchedule__link\" href=\"https://sports.yahoo.co.jp/keiba/race/index/2601020502\">"
                + "      <span class=\"hr-tableSchedule__title\">サラ系2歳未勝利</span>"
                + "    </a>"
                + "  </td>"
                + "  <td>1着</td><td>ミハテヌユメ</td></tr>"
                + "<tr class=\"hr-tableSchedule__evenline\"><td>2着</td><td>アルデシンザン</td></tr>"
                + "</tbody></table>";

        Document doc = Jsoup.parse(html);
        List<RaceParserService.RaceSchedule> schedules = service.getRaceSchedules(doc);

        assertEquals(2, schedules.size());

        assertEquals("https://sports.yahoo.co.jp/keiba/race/denma/2601020501", schedules.get(0).raceUrl());
        assertEquals(LocalTime.of(9, 50), schedules.get(0).raceTime());

        assertEquals("https://sports.yahoo.co.jp/keiba/race/denma/2601020502", schedules.get(1).raceUrl());
        assertEquals(LocalTime.of(10, 20), schedules.get(1).raceTime());
    }

    @Test
    void getRaceSchedules_shouldReturnEmptyWhenNoScheduleRowsFound() {
        Document doc = Jsoup.parse("<table><tbody><tr><td>該当なし</td></tr></tbody></table>");

        assertTrue(service.getRaceSchedules(doc).isEmpty());
    }

    @Test
    void isRaceStillShowableToday_shouldReturnTrueForRaceLongPast() {
        // 出馬表(/races)向けは、発走からどれだけ時間が経っていても
        // (同日である前提で)表示対象であり続ける
        LocalTime longPast = LocalTime.now(ZoneId.of("Asia/Tokyo")).minusMinutes(300);

        assertTrue(service.isRaceStillShowableToday(longPast));
    }

    @Test
    void isRaceStillShowableToday_shouldReturnFalseForRaceFarInTheFuture() {
        // 未来方向の制限(3時間)は予想向けと同じ
        LocalTime farFuture = LocalTime.now(ZoneId.of("Asia/Tokyo")).plusMinutes(200);

        assertFalse(service.isRaceStillShowableToday(farFuture));
    }

    @Test
    void isRaceStillShowableToday_shouldReturnFalseForNull() {
        assertFalse(service.isRaceStillShowableToday(null));
    }

    @Test
    void isWithinFinalOddsRefreshWindow_shouldReturnTrueWithinTenMinutesBeforePost() {
        LocalTime soon = LocalTime.now(ZoneId.of("Asia/Tokyo")).plusMinutes(5);

        assertTrue(service.isWithinFinalOddsRefreshWindow(soon));
    }

    @Test
    void isWithinFinalOddsRefreshWindow_shouldReturnFalseMoreThanTenMinutesBeforePost() {
        LocalTime tooEarly = LocalTime.now(ZoneId.of("Asia/Tokyo")).plusMinutes(11);

        assertFalse(service.isWithinFinalOddsRefreshWindow(tooEarly));
    }

    @Test
    void isWithinFinalOddsRefreshWindow_shouldReturnFalseAfterPostTime() {
        LocalTime justPast = LocalTime.now(ZoneId.of("Asia/Tokyo")).minusMinutes(1);

        assertFalse(service.isWithinFinalOddsRefreshWindow(justPast));
    }

    @Test
    void isWithinFinalOddsRefreshWindow_shouldReturnFalseForNull() {
        assertFalse(service.isWithinFinalOddsRefreshWindow(null));
    }
}
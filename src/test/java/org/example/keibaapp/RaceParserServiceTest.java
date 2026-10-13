package org.example.keibaapp;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
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

    // 発走時刻と現在時刻の差で判定するテストは、実時計から「現在+N分」を作ると
    // 日付またぎ(23時台に+30分が翌日0時台になる)や、判定側が少し後の現在時刻と
    // 比べることによる分への切り捨てで環境により結果が変わる(Linuxでは落ち、
    // 時計の刻みが粗いWindowsでは通るなど)ため、正午(JST)に固定した時計を使う
    private static final LocalTime FIXED_NOW = LocalTime.of(12, 0);

    private final RaceParserService fixedService = new RaceParserService(
            Clock.fixed(LocalDate.of(2026, 10, 4).atTime(FIXED_NOW).atZone(ZoneId.of("Asia/Tokyo")).toInstant(),
                    ZoneId.of("Asia/Tokyo")));

    private LocalTime fromNow(int minutes) {
        return FIXED_NOW.plusMinutes(minutes);
    }

    @Test
    void isRaceTimeRelevant_shouldReturnTrueForRaceStartingSoon() {
        assertTrue(fixedService.isRaceTimeRelevant(fromNow(30)));
    }

    @Test
    void isRaceTimeRelevant_shouldReturnTrueForRaceThatJustFinished() {
        assertTrue(fixedService.isRaceTimeRelevant(fromNow(-30)));
    }

    @Test
    void isRaceTimeRelevant_shouldReturnFalseForRaceFarInTheFuture() {
        assertFalse(fixedService.isRaceTimeRelevant(fromNow(200)));
    }

    @Test
    void isRaceTimeRelevant_shouldReturnFalseForRaceLongPast() {
        assertFalse(fixedService.isRaceTimeRelevant(fromNow(-90)));
    }

    @Test
    void isRaceTimeRelevant_shouldIncludeExactWindowBoundaries() {
        // 窓は発走60分後〜180分前(両端を含む)
        assertTrue(fixedService.isRaceTimeRelevant(fromNow(180)));
        assertFalse(fixedService.isRaceTimeRelevant(fromNow(181)));
        assertTrue(fixedService.isRaceTimeRelevant(fromNow(-60)));
        assertFalse(fixedService.isRaceTimeRelevant(fromNow(-61)));
    }

    @Test
    void isRaceTimeRelevant_shouldReturnFalseForNull() {
        assertFalse(fixedService.isRaceTimeRelevant(null));
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
        assertTrue(fixedService.isRaceStillShowableToday(LocalTime.of(7, 0)));
    }

    @Test
    void isRaceStillShowableToday_shouldReturnFalseForRaceFarInTheFuture() {
        // 未来方向の制限(3時間)は予想向けと同じ
        assertFalse(fixedService.isRaceStillShowableToday(fromNow(200)));
        assertTrue(fixedService.isRaceStillShowableToday(fromNow(180)));
    }

    @Test
    void isRaceStillShowableToday_shouldReturnFalseForNull() {
        assertFalse(fixedService.isRaceStillShowableToday(null));
    }

    @Test
    void isWithinFinalOddsRefreshWindow_shouldReturnTrueWithinTenMinutesBeforePost() {
        assertTrue(fixedService.isWithinFinalOddsRefreshWindow(fromNow(5)));
    }

    @Test
    void isWithinFinalOddsRefreshWindow_shouldReturnFalseMoreThanTenMinutesBeforePost() {
        assertFalse(fixedService.isWithinFinalOddsRefreshWindow(fromNow(11)));
    }

    @Test
    void isWithinFinalOddsRefreshWindow_shouldIncludeExactBoundaries() {
        // 発走10分前ちょうどと発走時刻ちょうどは窓に含まれる
        assertTrue(fixedService.isWithinFinalOddsRefreshWindow(fromNow(10)));
        assertTrue(fixedService.isWithinFinalOddsRefreshWindow(fromNow(0)));
    }

    @Test
    void isWithinFinalOddsRefreshWindow_shouldReturnFalseAfterPostTime() {
        assertFalse(fixedService.isWithinFinalOddsRefreshWindow(fromNow(-1)));
    }

    @Test
    void isWithinFinalOddsRefreshWindow_shouldReturnFalseForNull() {
        assertFalse(fixedService.isWithinFinalOddsRefreshWindow(null));
    }
}

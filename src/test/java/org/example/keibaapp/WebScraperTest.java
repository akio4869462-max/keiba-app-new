package org.example.keibaapp;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WebScraperTest {

    // 存在しないドメイン。接続自体ができずIOException（HttpStatusExceptionではない）
    // になるため、サイト障害時のサーキットブレーカーの検証に使う
    private static final String UNREACHABLE_URL =
            "https://this-domain-should-not-exist-keiba-app-test.invalid/";

    // 実在するドメインの存在しないパス。404はこのURL固有の問題であり、
    // サイト全体の障害ではないことの検証に使う
    private static final String NOT_FOUND_URL =
            "https://sports.yahoo.co.jp/keiba-app-test-not-found-path";

    @AfterEach
    void resetCircuitBreaker() {
        WebScraper.resetCircuitBreakerForTesting();
    }

    @Test
    void getHTML_shouldOpenCircuitAndSkipRetriesAfterRepeatedConnectionFailures() throws Exception {
        // サーキットが閉じている（正常な）間は残り秒数は0のはず
        assertEquals(0, WebScraper.getCircuitRemainingSeconds());

        // 1回目: 接続自体に失敗（3回リトライ）し、サーキットが開く
        assertThrows(IOException.class, () -> WebScraper.getHTML(UNREACHABLE_URL));

        // サーキットが開いた直後は残り秒数が正の値になっているはず（最大5分=300秒）
        long remaining = WebScraper.getCircuitRemainingSeconds();
        assertTrue(remaining > 0 && remaining <= 300,
                "サーキットオープン直後の残り秒数が想定外: " + remaining);

        // 2回目: サーキットが開いている間はリトライせず即座に失敗するはず
        long start = System.currentTimeMillis();
        assertThrows(IOException.class, () -> WebScraper.getHTML(UNREACHABLE_URL));
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 1000,
                "サーキットオープン中は即座に失敗するはずが" + elapsed + "ms かかった");
    }

    @Test
    void getHTML_shouldNotOpenCircuitOnNotFoundResponses() {
        // 404はこのURL固有の問題なので、何度起きてもサーキットは開かず、
        // 他の正常なURLへの取得には影響しないはず
        assertThrows(IOException.class, () -> WebScraper.getHTML(NOT_FOUND_URL));
        assertThrows(IOException.class, () -> WebScraper.getHTML(NOT_FOUND_URL));

        assertFalse(WebScraper.isCircuitOpenForTesting(),
                "404の連続発生でサーキットが開いてしまっている");
    }

    @Test
    void getPayouts_shouldCarryForwardBetTypeForRowspanRows() {
        // 複勝・ワイドは<th rowspan="3">が最初の行にしか付かないため、
        // <th>の無い2・3行目は直前の馬券種を引き継げるかを検証する
        String html = "<html><body>"
                + "<table class=\"hr-tableLeftTop\"><tbody>"
                + "<tr><th rowspan=\"1\">単勝</th><td>7</td><td>250円</td></tr>"
                + "</tbody></table>"
                + "<table class=\"hr-tableLeftTop\"><tbody>"
                + "<tr><th rowspan=\"3\">複勝</th><td>7</td><td>120円</td></tr>"
                + "<tr><td>3</td><td>180円</td></tr>"
                + "<tr><td>9</td><td>340円</td></tr>"
                + "</tbody></table>"
                + "</body></html>";

        Document doc = Jsoup.parse(html);
        List<PayoutEntry> payouts = WebScraper.getPayouts(doc);

        assertEquals(4, payouts.size());

        assertEquals("単勝", payouts.get(0).getBetType());
        assertEquals("7", payouts.get(0).getCombination());
        assertEquals(250, payouts.get(0).getPayoutYen());

        assertEquals("複勝", payouts.get(1).getBetType());
        assertEquals("7", payouts.get(1).getCombination());
        assertEquals(120, payouts.get(1).getPayoutYen());

        assertEquals("複勝", payouts.get(2).getBetType());
        assertEquals("3", payouts.get(2).getCombination());
        assertEquals(180, payouts.get(2).getPayoutYen());

        assertEquals("複勝", payouts.get(3).getBetType());
        assertEquals("9", payouts.get(3).getCombination());
        assertEquals(340, payouts.get(3).getPayoutYen());
    }

    private Document cornerRankDoc(String... rankTexts) {
        StringBuilder rows = new StringBuilder();

        for (String rankText : rankTexts) {
            rows.append("<tr class=\"hr-table__row\">")
                    .append("<td class=\"hr-table__data hr-table__data--corner\">コーナー</td>")
                    .append("<td class=\"hr-table__data hr-table__data--rank\">")
                    .append(rankText)
                    .append("</td></tr>");
        }

        String html = "<html><body><div class=\"hr-cornerRank\"><table class=\"hr-table\">"
                + "<tbody>" + rows + "</tbody></table></div></body></html>";

        return Jsoup.parse(html);
    }

    @Test
    void getCornerLatMeans_shouldGiveInnerHorseLatZeroAndOuterHorseLatOne() {
        // MODEL_REVISION.md §6.1の実例(2609040504・1コーナー)。
        // (8,11)の組だけが括弧グループで、8が内側(lat=0)・11が外側(lat=1)、
        // それ以外の単独馬は全てlat=0になるはず
        Document doc = cornerRankDoc("10-4(8,11)2,5-3,6=12=9-7=1");

        Map<Integer, Double> latMeans = WebScraper.getCornerLatMeans(doc);

        assertEquals(0.0, latMeans.get(10));
        assertEquals(0.0, latMeans.get(4));
        assertEquals(0.0, latMeans.get(8));
        assertEquals(1.0, latMeans.get(11));
        assertEquals(0.0, latMeans.get(2));
        assertEquals(0.0, latMeans.get(12));
        assertEquals(0.0, latMeans.get(1));
    }

    @Test
    void getCornerLatMeans_shouldStripAsteriskMarkerWithoutAffectingGrouping() {
        // MODEL_REVISION.md §3.1の例。"*"は特に意味を持たないマーカーなので、
        // 除去しても(5,2)の組の解釈(5=内側lat0, 2=外側lat1)は変わらないはず
        Document doc = cornerRankDoc("1(*5,2)-(3,4)1");

        Map<Integer, Double> latMeans = WebScraper.getCornerLatMeans(doc);

        assertEquals(0.0, latMeans.get(5));
        assertEquals(1.0, latMeans.get(2));
        assertEquals(0.0, latMeans.get(3));
        assertEquals(1.0, latMeans.get(4));
        assertEquals(0.0, latMeans.get(1));
    }

    @Test
    void getCornerLatMeans_shouldHandleHtmlMarkupInsideRankCell() {
        // 実際のページでは先頭馬が<span class="hr-cornerRank__first">でハイライトされる
        // ((3,<span>12</span>)のように数字の途中にタグが挟まる)。text()で正しく
        // 連結され、12が(3,12)の組の外側(lat=1)として扱われるはず
        Document doc = cornerRankDoc(
                "8,9(4,14)(3,<span class=\"hr-cornerRank__first\">12</span>)(6,13)10,1(2,11)-(5,15)-7");

        Map<Integer, Double> latMeans = WebScraper.getCornerLatMeans(doc);

        assertEquals(0.0, latMeans.get(3));
        assertEquals(1.0, latMeans.get(12));
        assertEquals(0.0, latMeans.get(4));
        assertEquals(1.0, latMeans.get(14));
    }

    @Test
    void getCornerLatMeans_shouldAverageLatAcrossMultipleCorners() {
        // 同じ馬(11)が1コーナーでlat=1、2コーナーでlat=0の組に入る場合、
        // lat_meanは(1+0)/2=0.5になるはず(欠測コーナーは無視する仕様だが、
        // ここでは2コーナーとも出現するケースを確認する)
        Document doc = cornerRankDoc(
                "10-4(8,11)2,5-3,6=12=9-7=1",
                "10-4(11,8)2,5-3,6=12=9-7=1");

        Map<Integer, Double> latMeans = WebScraper.getCornerLatMeans(doc);

        assertEquals(0.5, latMeans.get(11));
        assertEquals(0.5, latMeans.get(8));
    }

    @Test
    void getCornerLatMeans_shouldReturnEmptyWhenNoCornerRankSection() {
        Document doc = Jsoup.parse("<html><body><p>コーナー通過順位なし</p></body></html>");

        Map<Integer, Double> latMeans = WebScraper.getCornerLatMeans(doc);

        assertTrue(latMeans.isEmpty());
    }

    @Test
    void getBreeder_shouldExtractBreederFromProfileSection() {
        // 実際の馬詳細ページのプロフィール欄(生年月日・毛色・調教師等と同じ並び)を再現
        String html = "<html><body><div class=\"hr-profile\">"
                + "<div class=\"hr-profile__item\">"
                + "<dt class=\"hr-profile__title\">生年月日</dt>"
                + "<dd class=\"hr-profile__text\">2023年2月9日</dd></div>"
                + "<div class=\"hr-profile__item\">"
                + "<dt class=\"hr-profile__title\">生産者</dt>"
                + "<dd class=\"hr-profile__text\">ノーザンファーム</dd></div>"
                + "<div class=\"hr-profile__item\">"
                + "<dt class=\"hr-profile__title\">産地</dt>"
                + "<dd class=\"hr-profile__text\">安平町</dd></div>"
                + "</div></body></html>";

        Document doc = Jsoup.parse(html);

        assertEquals("ノーザンファーム", WebScraper.getBreeder(doc));
    }

    @Test
    void getBreeder_shouldReturnEmptyWhenProfileSectionMissing() {
        Document doc = Jsoup.parse("<html><body><p>プロフィールなし</p></body></html>");

        assertEquals("", WebScraper.getBreeder(doc));
    }
}

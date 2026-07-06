package org.example.keibaapp;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

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
        // 1回目: 接続自体に失敗（3回リトライ）し、サーキットが開く
        assertThrows(IOException.class, () -> WebScraper.getHTML(UNREACHABLE_URL));

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
}

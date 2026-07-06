package org.example.keibaapp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

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
}

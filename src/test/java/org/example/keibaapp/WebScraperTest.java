package org.example.keibaapp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class WebScraperTest {

    private static final String BAD_URL = "https://example.com/keiba-circuit-breaker-test-path";

    @AfterEach
    void resetCircuitBreaker() {
        WebScraper.resetCircuitBreakerForTesting();
    }

    @Test
    void getHTML_shouldOpenCircuitAndSkipRetriesAfterRepeatedFailures() throws Exception {
        // 1回目: 3回リトライして失敗し、サーキットが開く
        assertThrows(IOException.class, () -> WebScraper.getHTML(BAD_URL));

        // 2回目: サーキットが開いている間はリトライせず即座に失敗するはず
        long start = System.currentTimeMillis();
        assertThrows(IOException.class, () -> WebScraper.getHTML(BAD_URL));
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 1000,
                "サーキットオープン中は即座に失敗するはずが" + elapsed + "ms かかった");
    }
}

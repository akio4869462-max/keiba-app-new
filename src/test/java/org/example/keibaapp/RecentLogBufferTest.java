package org.example.keibaapp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecentLogBufferTest {

    private final PrintStream originalOut = System.out;

    @AfterEach
    void restoreStdout() {
        System.setOut(originalOut);
    }

    @Test
    void install_shouldCaptureLinesIncludingMultibyteTextAndStillPassThrough() {
        RecentLogBuffer buffer = new RecentLogBuffer();
        buffer.install();

        System.out.println("開催日判定でエラーが発生しました: connection reset");
        System.out.print("改行なしの途中");
        System.out.println("つづき");

        List<String> lines = buffer.recent(10);

        assertEquals(List.of("開催日判定でエラーが発生しました: connection reset", "改行なしの途中つづき"), lines);
    }

    @Test
    void recent_shouldKeepOnlyTheLatestLinesUpToLimit() {
        RecentLogBuffer buffer = new RecentLogBuffer();

        for (int i = 0; i < RecentLogBuffer.MAX_LINES + 20; i++) {
            buffer.addLine("line" + i);
        }

        List<String> all = buffer.recent(RecentLogBuffer.MAX_LINES + 100);

        assertEquals(RecentLogBuffer.MAX_LINES, all.size());
        assertEquals("line20", all.get(0));
        assertEquals("line" + (RecentLogBuffer.MAX_LINES + 19), all.get(all.size() - 1));
        assertEquals(List.of("line" + (RecentLogBuffer.MAX_LINES + 18), "line" + (RecentLogBuffer.MAX_LINES + 19)),
                buffer.recent(2));
    }

    @Test
    void accept_shouldIgnoreCarriageReturns() {
        RecentLogBuffer buffer = new RecentLogBuffer();

        for (byte b : "abc\r\n".getBytes(StandardCharsets.UTF_8)) {
            buffer.accept(b);
        }

        assertEquals(List.of("abc"), buffer.recent(5));
    }
}

package org.example.keibaapp;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

// アプリが標準出力(System.out)に出した直近の行をメモリに残す。本番のログへ直接アクセスする手段が無く、
// 「なぜダミーに戻ったか」「板の取得がなぜ空か」を切り分けられなかったため、/status/log で読めるようにする。
// 元の標準出力にもそのまま流す(docker logs等は従来どおり)。メモリ上の固定長なので再起動で消える
@Component
public class RecentLogBuffer {

    static final int MAX_LINES = 500;

    private final Deque<String> lines = new ArrayDeque<>();
    private final ByteArrayOutputStream pending = new ByteArrayOutputStream();

    @PostConstruct
    void install() {
        PrintStream original = System.out;

        System.setOut(new PrintStream(new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                original.write(b);
                accept(b);
            }

            @Override
            public void write(byte[] bytes, int off, int len) throws IOException {
                original.write(bytes, off, len);
                for (int i = off; i < off + len; i++) {
                    accept(bytes[i]);
                }
            }

            @Override
            public void flush() throws IOException {
                original.flush();
            }
        }, true, StandardCharsets.UTF_8));
    }

    // 1バイトずつ改行で区切って行にする(UTF-8の途中で切れないよう、バイト列のまま溜めてから復号する)
    synchronized void accept(int b) {
        if (b == '\n') {
            addLine(pending.toString(StandardCharsets.UTF_8));
            pending.reset();
        } else if (b != '\r') {
            pending.write(b);
        }
    }

    synchronized void addLine(String line) {
        if (lines.size() >= MAX_LINES) {
            lines.removeFirst();
        }
        lines.addLast(line);
    }

    // 古い順の直近max行
    public synchronized List<String> recent(int max) {
        List<String> all = new ArrayList<>(lines);
        return all.subList(Math.max(0, all.size() - max), all.size());
    }
}

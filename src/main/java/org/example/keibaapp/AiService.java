package org.example.keibaapp;

import org.springframework.stereotype.Service;

@Service
public class AiService {

    public String createComment(Horse horse) {
        if (horse.getAiPrompt() == null || horse.getAiPrompt().isBlank()) {
            return "AIコメント生成用の情報が不足しています。";
        }

        return "AIコメント生成は未実装です。";
    }
}
package org.example.keibaapp;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AiPromptServiceTest {

    @Test
    void createPrompt_shouldContainRaceAndHorseInfo() {
        AiPromptService service = new AiPromptService();

        Horse horse = new Horse(
                "1",
                "1",
                "テストホース",
                "テスト騎手",
                "57.0",
                5.2
        );

        horse.setPredictionScore(82.6);
        horse.setPredictionReason("""
                ・オッズ評価 +9.6
                ・距離適性 +8.3
                ・コース適性 +5.7
                """);

        horse.setLastRace(new PastRaceInfo("前走レース 阪神 芝1600m 18頭", 1, "GI", 2));
        horse.setSecondLastRace(new PastRaceInfo("2走前レース 京都 芝1600m 16頭", 2, "GII", 3));
        horse.setThirdLastRace(new PastRaceInfo("3走前レース 東京 芝1800m 12頭", 3, "GIII", 4));

        RaceInfo race = new RaceInfo(
                11,
                "阪神",
                "テストステークス GIII",
                LocalTime.of(15, 35),
                "芝",
                "1600m",
                List.of(horse)
        );

        String prompt = service.createPrompt(race, horse);

        assertTrue(prompt.contains("競馬場: 阪神"));
        assertTrue(prompt.contains("レース名: テストステークス GIII"));
        assertTrue(prompt.contains("距離: 1600m"));
        assertTrue(prompt.contains("出走頭数: 1頭"));
        assertTrue(prompt.contains("この馬の予想順位: 1位"));
        assertTrue(prompt.contains("馬名: テストホース"));
        assertTrue(prompt.contains("騎手: テスト騎手"));
        assertTrue(prompt.contains("予想スコア: 82.6"));
        assertTrue(prompt.contains("【上位予想馬】"));
        assertTrue(prompt.contains("【過去3走】"));
        assertTrue(prompt.contains("期待できる点"));
        assertTrue(prompt.contains("不安な点"));
        assertTrue(prompt.contains("総合評価"));
    }
}
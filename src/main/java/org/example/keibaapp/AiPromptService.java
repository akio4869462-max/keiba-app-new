package org.example.keibaapp;

import org.springframework.stereotype.Service;
import java.util.Objects;

@Service
public class AiPromptService {

    public String createPrompt(
            RaceInfo race,
            Horse horse) {

        StringBuilder prompt = new StringBuilder();

        prompt.append("あなたは競馬新聞の記者です。\n");
        prompt.append("以下の情報をもとに、この馬の評価コメントを作成してください。\n\n");

        prompt.append("【レース情報】\n");
        prompt.append("競馬場: ").append(race.getVenue()).append("\n");
        prompt.append("レース名: ").append(race.getRaceName()).append("\n");
        prompt.append("コース: ").append(race.getCourse()).append("\n");
        prompt.append("距離: ").append(race.getDistance()).append("\n");
        prompt.append("出走頭数: ").append(race.getHorses().size()).append("頭\n\n");

        prompt.append("【レース内での位置づけ】\n");
        prompt.append("この馬の予想順位: ")
                .append(getPredictionRank(race, horse))
                .append("位\n\n");
        appendTopHorses(prompt, race);

        prompt.append("【馬情報】\n");
        prompt.append("馬名: ").append(horse.getName()).append("\n");
        prompt.append("騎手: ").append(horse.getJockeyName()).append("\n");
        prompt.append("オッズ: ").append(horse.getOdds()).append("\n");
        prompt.append("予想スコア: ")
                .append(String.format("%.1f", horse.getPredictionScore()))
                .append("\n\n");

        prompt.append("【アプリの評価理由】\n");
        prompt.append(horse.getPredictionReason()).append("\n\n");

        prompt.append("【過去3走】\n");
        appendPastRace(prompt, "前走", horse.getLastRace());
        appendPastRace(prompt, "2走前", horse.getSecondLastRace());
        appendPastRace(prompt, "3走前", horse.getThirdLastRace());

        prompt.append("\n【出力条件】\n");
        prompt.append("・期待できる点\n");
        prompt.append("・不安な点\n");
        prompt.append("・総合評価\n");
        prompt.append("上記3項目を、合計100文字程度で簡潔に書いてください。\n");

        return prompt.toString();
    }

    private void appendPastRace(
            StringBuilder prompt,
            String label,
            PastRaceInfo race) {

        prompt.append(label).append(": ");

        if (race == null || race.getRank() == 0) {
            prompt.append("データなし\n");
            return;
        }

        prompt.append(race.getDisplayText()).append("\n");
    }

    private int getPredictionRank(RaceInfo race, Horse targetHorse) {
        for (int i = 0; i < race.getHorses().size(); i++) {
            Horse horse = race.getHorses().get(i);

            if (horse == targetHorse
                    || Objects.equals(horse.getName(), targetHorse.getName())) {
                return i + 1;
            }
        }

        return 0;
    }

    private void appendTopHorses(
            StringBuilder prompt,
            RaceInfo race) {

        prompt.append("【上位予想馬】\n");

        int limit = Math.min(3, race.getHorses().size());

        for (int i = 0; i < limit; i++) {
            Horse horse = race.getHorses().get(i);

            prompt.append(i + 1)
                    .append("位 ")
                    .append(horse.getName())
                    .append(" ")
                    .append(String.format("%.1f", horse.getPredictionScore()))
                    .append("点\n");
        }

        prompt.append("\n");
    }
}
package org.example.keibaapp;

import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class DummyRaceFactory {

    private final PredictionService predictionService;

    public DummyRaceFactory(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    public List<RaceInfo> createDummyRaces() {
        List<Horse> horses1 = new ArrayList<>();
        List<Horse> horses2 = new ArrayList<>();

        horses1.add(new Horse("1", "1", "ダノンデサイル", "戸崎 圭太", "58.0", 7.3));
        horses1.add(new Horse("1", "2", "ミュージアムマイル", "D.レーン", "54.0", 5.3));
        horses1.add(new Horse("2", "3", "シュガークン", "横山 武史", "58.0", 303.2));
        horses1.add(new Horse("2", "4", "ミクニインスパイア", "丹内 祐次", "54.0", 65.3));
        horses1.add(new Horse("3", "5", "クロワデュノール", "北村 友一", "54.0", 1.8));

        horses2.add(new Horse("3", "6", "ビザンチンドリーム", "西村 淳也", "58.0", 36.5));
        horses2.add(new Horse("4", "7", "ファミリータイム", "幸 英明", "58.0", 332.8));
        horses2.add(new Horse("4", "8", "タガノデュード", "高杉 吏麒", "58.0", 74.6));
        horses2.add(new Horse("5", "9", "コスモキュランダ", "横山 武史", "58.0", 44.5));
        horses2.add(new Horse("5", "10", "ジューンテイク", "松山 弘平", "58.0", 199.2));

        List<RaceInfo> races1 = new ArrayList<>();
        List<RaceInfo> races2 = new ArrayList<>();

        races1.add(new RaceInfo(
                1,
                "東京",
                "ダミーレース1",
                LocalTime.of(10, 0),
                "芝",
                "2000m",
                horses1
        ));
        races2.add(new RaceInfo(
                2,
                "東京",
                "ダミーレース2",
                LocalTime.of(10, 30),
                "ダ",
                "1600m",
                horses2
        ));

        PastRaceInfo[] lastRaces = {
                new PastRaceInfo("ダミーレース", 3, "GI", 4),
                new PastRaceInfo("ダミーレース", 1, "GIII", 2),
                new PastRaceInfo("ダミーレース", 15, "条件戦", 8),
                new PastRaceInfo("ダミーレース", 2, "条件戦", 3),
                new PastRaceInfo("ダミーレース", 1, "GI", 1)
        };

        PastRaceInfo[] secondLastRaces = {
                new PastRaceInfo("ダミーレース", 5, "GII", 3),
                new PastRaceInfo("ダミーレース", 2, "GI", 5),
                new PastRaceInfo("ダミーレース", 8, "条件戦", 6),
                new PastRaceInfo("ダミーレース", 1, "条件戦", 2),
                new PastRaceInfo("ダミーレース", 2, "GI", 1)
        };

        PastRaceInfo[] thirdLastRaces = {
                new PastRaceInfo("ダミーレース", 2, "GIII", 2),
                new PastRaceInfo("ダミーレース", 1, "GII", 4),
                new PastRaceInfo("ダミーレース", 10, "条件戦", 10),
                new PastRaceInfo("ダミーレース", 4, "条件戦", 5),
                new PastRaceInfo("ダミーレース", 1, "GIII", 1)
        };

        for (int i = 0; i < horses1.size(); i++) {
            Horse horse = horses1.get(i);
            horse.setLastRace(lastRaces[i]);
            horse.setSecondLastRace(secondLastRaces[i]);
            horse.setThirdLastRace(thirdLastRaces[i]);
        }

        for (Horse horse : horses1) {
            horse.setPredictionScore(predictionService.calculateExpectedValue(horse, horses1));
            horse.setPredictionReason(predictionService.createReason(horse, "芝", "2000m"));
        }

        horses1.sort(Comparator.comparingDouble(Horse::getPredictionScore).reversed());

        for (int i = 0; i < horses2.size(); i++) {
            Horse horse = horses2.get(i);
            horse.setLastRace(lastRaces[i]);
            horse.setSecondLastRace(secondLastRaces[i]);
            horse.setThirdLastRace(thirdLastRaces[i]);
        }

        for (Horse horse : horses2) {
            horse.setPredictionScore(predictionService.calculateExpectedValue(horse, horses2));
            horse.setPredictionReason(predictionService.createReason(horse, "ダ", "1600m"));
        }

        horses2.sort(Comparator.comparingDouble(Horse::getPredictionScore).reversed());

        List<RaceInfo> races = new ArrayList<>();
        races.addAll(races1);
        races.addAll(races2);
        return races;
    }
}

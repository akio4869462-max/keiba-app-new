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
        List<Horse> horses = new ArrayList<>();

        horses.add(new Horse("1", "1", "ダノンデサイル", "戸崎 圭太", "58.0", 7.3));
        horses.add(new Horse("1", "2", "ミュージアムマイル", "D.レーン", "54.0", 5.3));
        horses.add(new Horse("2", "3", "シュガークン", "吉村 誠之助", "58.0", 303.2));
        horses.add(new Horse("2", "4", "ミクニインスパイア", "丹内 祐次", "54.0", 65.3));
        horses.add(new Horse("3", "5", "クロワデュノール", "北村 友一", "54.0",1.8));
        horses.add(new Horse("3", "6", "ビザンチンドリーム", "西村 淳也", "58.0", 36.5));
        horses.add(new Horse("4", "7", "ファミリータイム", "幸 英明", "58.0", 332.8));
        horses.add(new Horse("4", "8", "タガノデュード", "高杉 吏麒", "58.0", 74.6));
        horses.add(new Horse("5", "9", "コスモキュランダ", "横山 武史", "58.0", 44.5));
        horses.add(new Horse("5", "10", "ジューンテイク", "松山 弘平", "58.0", 199.2));
        horses.add(new Horse("6", "11", "シンエンペラー", "坂井 瑠星", "58.0", 102.4));
        horses.add(new Horse("6", "12", "マイネルエンペラー", "川田 将雅", "58.0", 132.7));
        horses.add(new Horse("7", "13", "シェイクユアハート", "古川 吉洋", "58.0", 55.2));
        horses.add(new Horse("7", "14", "スティンガーグラス", "岩田 望来", "58.0", 125.0));
        horses.add(new Horse("7", "15", "マイユニバース", "横山 典弘", "54.0", 23.3));
        horses.add(new Horse("8", "16", "メイショウタバル", "武 豊", "58.0", 4.9));
        horses.add(new Horse("8", "17", "レガレイラ", "C.ルメール", "56.0", 10.5));
        horses.add(new Horse("8", "18", "ミステリーウェイ", "松本 大輝", "58.0", 249.3));
        List<RaceInfo> races = new ArrayList<>();

        races.add(new RaceInfo(
                1,
                "東京",
                "ダミーレース",
                LocalTime.of(10, 0),
                "芝",
                "2000m",
                horses
        ));

        PastRaceInfo[] lastRaces = {
                new PastRaceInfo("ダミーレース",3, "GI", 4),
                new PastRaceInfo("ダミーレース",1, "GIII", 2),
                new PastRaceInfo("ダミーレース",15, "条件戦", 8),
                new PastRaceInfo("ダミーレース",2, "条件戦", 3),
                new PastRaceInfo("ダミーレース",1, "GI", 1),
                new PastRaceInfo("ダミーレース",2, "GII", 5),
                new PastRaceInfo("ダミーレース",9, "条件戦", 10),
                new PastRaceInfo("ダミーレース",6, "条件戦", 7),
                new PastRaceInfo("ダミーレース",7, "GIII", 6),
                new PastRaceInfo("ダミーレース",4, "条件戦", 4),
                new PastRaceInfo("ダミーレース",7, "GI", 3),
                new PastRaceInfo("ダミーレース",5, "GII", 5),
                new PastRaceInfo("ダミーレース",1, "OP", 2),
                new PastRaceInfo("ダミーレース",1, "L", 1),
                new PastRaceInfo("ダミーレース",1, "条件戦", 1),
                new PastRaceInfo("ダミーレース",2, "GIII", 1),
                new PastRaceInfo("ダミーレース",4, "GI", 2),
                new PastRaceInfo("ダミーレース",8, "条件戦", 9)
        };

        PastRaceInfo[] secondLastRaces = {
                new PastRaceInfo("ダミーレース",5, "GII", 3),
                new PastRaceInfo("ダミーレース",2, "GI", 5),
                new PastRaceInfo("ダミーレース",8, "条件戦", 6),
                new PastRaceInfo("ダミーレース",1, "条件戦", 2),
                new PastRaceInfo("ダミーレース",2, "GI", 1),
                new PastRaceInfo("ダミーレース",6, "GIII", 4),
                new PastRaceInfo("ダミーレース",12, "条件戦", 12),
                new PastRaceInfo("ダミーレース",4, "条件戦", 8),
                new PastRaceInfo("ダミーレース",3, "GIII", 5),
                new PastRaceInfo("ダミーレース",7, "条件戦", 9),
                new PastRaceInfo("ダミーレース",3, "GII", 2),
                new PastRaceInfo("ダミーレース",6, "GIII", 6),
                new PastRaceInfo("ダミーレース",4, "OP", 3),
                new PastRaceInfo("ダミーレース",5, "L", 4),
                new PastRaceInfo("ダミーレース",2, "条件戦", 2),
                new PastRaceInfo("ダミーレース",1, "GII", 1),
                new PastRaceInfo("ダミーレース",1, "GI", 3),
                new PastRaceInfo("ダミーレース",10, "条件戦", 11)
        };

        PastRaceInfo[] thirdLastRaces = {
                new PastRaceInfo("ダミーレース",2, "GIII", 2),
                new PastRaceInfo("ダミーレース",1, "GII", 4),
                new PastRaceInfo("ダミーレース",10, "条件戦", 10),
                new PastRaceInfo("ダミーレース",4, "条件戦", 5),
                new PastRaceInfo("ダミーレース",1, "GIII", 1),
                new PastRaceInfo("ダミーレース",3, "GII", 3),
                new PastRaceInfo("ダミーレース",8, "条件戦", 9),
                new PastRaceInfo("ダミーレース",5, "条件戦", 7),
                new PastRaceInfo("ダミーレース",6, "GI", 8),
                new PastRaceInfo("ダミーレース",5, "条件戦", 6),
                new PastRaceInfo("ダミーレース",2, "GI", 2),
                new PastRaceInfo("ダミーレース",4, "GII", 4),
                new PastRaceInfo("ダミーレース",6, "OP", 5),
                new PastRaceInfo("ダミーレース",2, "L", 2),
                new PastRaceInfo("ダミーレース",3, "条件戦", 3),
                new PastRaceInfo("ダミーレース",5, "GI", 6),
                new PastRaceInfo("ダミーレース",2, "GII", 2),
                new PastRaceInfo("ダミーレース",12, "条件戦", 12)
        };

        for (int i = 0; i < horses.size(); i++) {
            Horse horse = horses.get(i);

            horse.setLastRace(lastRaces[i]);
            horse.setSecondLastRace(secondLastRaces[i]);
            horse.setThirdLastRace(thirdLastRaces[i]);

            horse.setPredictionScore(predictionService.calculateScore(horse,"2000m"));
            horse.setPredictionReason(predictionService.createReason(horse, "2000m"));
        }

        horses.sort(
                Comparator.comparingDouble(Horse::getPredictionScore)
                        .reversed()
        );

        return races;
    }
}
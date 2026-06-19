package org.example.keibaapp;

import org.springframework.stereotype.Component;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

@Component
public class CsvExporter {

    public void exportPredictions(List<RaceInfo> races) {
        String fileName = "prediction_results.csv";

        try (FileWriter writer = new FileWriter(fileName)) {

            writer.write("race_name,horse_name,odds,prediction_rank,prediction_score,"
                    + "last_race,last_rank,last_popularity,"
                    + "second_race,second_rank,second_popularity,"
                    + "third_race,third_rank,third_popularity,"
                    + "actual_race,actual_rank\n");

            for (RaceInfo race : races) {
                int predictionRank = 1;

                for (Horse horse : race.getHorses()) {
                    writer.write(toCsvLine(race, horse, predictionRank));
                    predictionRank++;
                }
            }

            System.out.println("CSV出力完了: " + fileName);

        } catch (IOException e) {
            System.out.println("CSV出力失敗: " + e.getMessage());
        }
    }

    private String toCsvLine(RaceInfo race, Horse horse, int predictionRank) {
        PastRaceInfo last = horse.getLastRace();
        PastRaceInfo second = horse.getSecondLastRace();
        PastRaceInfo third = horse.getThirdLastRace();
        PastRaceInfo actual = horse.getActualRace();

        return csv(race.getDisplayRaceName()) + ","
                + csv(horse.getName()) + ","
                + horse.getOdds() + ","
                + predictionRank + ","
                + String.format("%.1f", horse.getPredictionScore()) + ","
                + pastRaceCsv(last) + ","
                + pastRaceCsv(second) + ","
                + pastRaceCsv(third) + ","
                + pastRaceCsv(actual)
                + "\n";
    }

    private String pastRaceCsv(PastRaceInfo race) {
        if (race == null) {
            return csv("データなし") + ",0,0";
        }

        return csv(race.getRaceName()) + ","
                + race.getRank() + ","
                + race.getPopularity();
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }

        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
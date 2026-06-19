package org.example.keibaapp;

import org.springframework.stereotype.Service;

@Service
public class PredictionService {
    private double calculatePastRaceScore(PastRaceInfo race, double weight) {
        if (race == null || race.getRank() == 0) {
            return 0;
        }

        double score = 0;

        int rank = race.getRank();

        if (rank == 1) {
            score += 25;
        } else if (rank == 2) {
            score += 18;
        } else if (rank == 3) {
            score += 10;
        } else if (rank >= 4 && rank <= 5) {
            score += 5;
        }

        String grade = race.getGrade();

        if ("GI".equals(grade)) {
            score += 30;
        } else if ("GII".equals(grade)) {
            score += 20;
        } else if ("GIII".equals(grade)) {
            score += 12;
        } else if ("L".equals(grade)) {
            score += 7;
        } else if ("OP".equals(grade)) {
            score += 5;
        }

        int popularity = race.getPopularity();

        if (popularity == 1) {
            score += 10;
        } else if (popularity <= 3 && popularity > 0) {
            score += 7;
        } else if (popularity <= 5 && popularity > 0) {
            score += 4;
        }

        return score * weight;
    }

    private String createPastRaceReason(String label, PastRaceInfo race, double weight) {
        if (race == null || race.getRank() == 0) {
            return label + "データなし";
        }

        double score = calculatePastRaceScore(race, weight);

        return label
                + race.getGrade()
                + " "
                + race.getRank()
                + "着"
                + " "
                + race.getPopularity()
                + "人気"
                + " +"
                + String.format("%.1f", score);
    }

    public String createReason(Horse horse) {
        StringBuilder reason = new StringBuilder();

        if (horse.getOdds() > 0 && horse.getOdds() < 999.9) {
            reason.append("オッズ評価 +")
                    .append(String.format("%.1f", 50 / horse.getOdds()));
        } else {
            reason.append("オッズ評価なし");
        }

        reason.append(" / ");
        reason.append(createPastRaceReason("前走: ", horse.getLastRace(), 1.0));

        reason.append(" / ");
        reason.append(createPastRaceReason("2走前: ", horse.getSecondLastRace(), 0.6));

        reason.append(" / ");
        reason.append(createPastRaceReason("3走前: ", horse.getThirdLastRace(), 0.3));

        return reason.toString();
    }

    public double calculateScore(Horse horse) {
        double score = 0;

        if (horse.getOdds() > 0 && horse.getOdds() < 999.9) {
            score += 50 / horse.getOdds();
//            score += 20 / horse.getOdds();
//            score += 0;
        }

        score += calculatePastRaceScore(horse.getLastRace(), 1.0);
        score += calculatePastRaceScore(horse.getSecondLastRace(), 0.6);
        score += calculatePastRaceScore(horse.getThirdLastRace(), 0.3);

        return score;
    }
}
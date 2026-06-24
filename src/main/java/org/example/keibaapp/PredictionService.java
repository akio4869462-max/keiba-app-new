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

    public String createReason(Horse horse, String currentDistance) {
        StringBuilder reason = new StringBuilder();

        if (horse.getOdds() > 0 && horse.getOdds() < 999.9) {
            reason.append("オッズ評価 +")
                    .append(String.format("%.1f", 50 / horse.getOdds()));
        } else {
            reason.append("オッズ評価なし");
        }

        double distanceScore = 0;
        distanceScore += calculateDistanceScore(currentDistance, horse.getLastRace());
        distanceScore += calculateDistanceScore(currentDistance, horse.getSecondLastRace()) * 0.6;
        distanceScore += calculateDistanceScore(currentDistance, horse.getThirdLastRace()) * 0.3;

        reason.append(" / 距離適性 +")
                .append(String.format("%.1f", distanceScore));

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

    public double calculateScore(Horse horse, String currentDistance) {
        double score = calculateScore(horse);

        score += calculateDistanceScore(currentDistance, horse.getLastRace());
        score += calculateDistanceScore(currentDistance, horse.getSecondLastRace()) * 0.6;
        score += calculateDistanceScore(currentDistance, horse.getThirdLastRace()) * 0.3;

        return score;
    }

    private int parseDistance(String distanceText) {
        if (distanceText == null || distanceText.isBlank()) {
            return 0;
        }

        String number = distanceText.replaceAll("[^0-9]", "");

        if (number.isEmpty()) {
            return 0;
        }

        return Integer.parseInt(number);
    }

    private double calculateDistanceScore(String currentDistance, PastRaceInfo pastRace) {
        if (pastRace == null || pastRace.getRank() == 0) {
            return 0;
        }

        int current = parseDistance(currentDistance);
        int past = parseDistance(pastRace.getDistance());

        if (current == 0 || past == 0) {
            return 0;
        }

        int diff = Math.abs(current - past);

        if (diff == 0) {
            return 5;
        }

        if (diff <= 200) {
            return 3;
        }

        if (diff <= 400) {
            return 1;
        }

        return 0;
    }
}
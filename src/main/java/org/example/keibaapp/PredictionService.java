package org.example.keibaapp;

import org.springframework.data.geo.Distance;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class PredictionService {
    private static final Map<Integer, Integer> RANK_SCORES = Map.of(
            1, 25,
            2, 18,
            3, 1
    );

    private static final Map<String, Integer> GRADE_SCORES = Map.of(
            "GI", 30,
            "GII", 20,
            "GIII", 12,
            "L", 7,
            "OP", 5
    );


    private List<PastRaceInfo> getRaceHistory(Horse horse) {
        return List.of(
                horse.getLastRace(),
                horse.getSecondLastRace(),
                horse.getThirdLastRace()
        );
    }

    private static final List<Double> WEIGHTS = List.of(1.0, 0.6, 0.3);

    private static final List<String> RACE_LABELS = List.of("前走: ", "2走前: ", "3走前: ");

    private double calculatePastRaceScore(PastRaceInfo race, double weight) {
        if (race == null || race.getRank() == 0) {
            return 0;
        }

        double score = 0;


        int rank = race.getRank();

        score += RANK_SCORES.getOrDefault(rank, rank <= 5 ? 5 : 0);

        String grade = race.getGrade();

        score += GRADE_SCORES.getOrDefault(grade, 0);

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

    public String createReason(
            Horse horse,
            String currentCourse,
            String currentDistance) {

        StringBuilder reason = new StringBuilder();

        if (horse.getOdds() > 0 && horse.getOdds() < 999.9) {
            reason.append("・オッズ評価 +")
                    .append(String.format("%.1f", 50 / horse.getOdds()));
        } else {
            reason.append("オッズ評価なし");
        }

        double distanceScore = 0;

        List<PastRaceInfo> races = getRaceHistory(horse);

        for (int i = 0; i < races.size(); i++) {
            distanceScore += calculateDistanceScore(currentDistance, races.get(i)) * WEIGHTS.get(i);
        }

        double courseScore = 0;

        for (int i = 0; i < races.size(); i++) {
            courseScore += calculateCourseScore(currentCourse, races.get(i)) * WEIGHTS.get(i);
        }

        reason.append("\n・距離適性 +")
                .append(String.format("%.1f", distanceScore));

        reason.append("\n・コース適性 +")
                .append(String.format("%.1f", courseScore));

        for(int i=0;i<races.size();i++){
            reason.append("\n・");
            reason.append(createPastRaceReason(RACE_LABELS.get(i), races.get(i), WEIGHTS.get(i)));
        }

        return reason.toString();
    }

    public double calculateScore(Horse horse) {
        double score = 0;

        List<PastRaceInfo> races = getRaceHistory(horse);

        for (int i = 0; i < races.size(); i++) {
            score += calculatePastRaceScore(races.get(i), WEIGHTS.get(i));
        }

        return score;
    }

    public double calculateExpectedValue(Horse horse, List<Horse> allHorse) {
        double totalScore = 0;

        for (Horse h : allHorse) {
            totalScore += calculateScore(h);
        }

        if (totalScore == 0 || horse.getOdds() <= 0 || horse.getOdds() >= 999.9) {
            return 0;
        }

        return horse.getOdds() * calculateScore(horse) / totalScore;
    }

    public double calculateScore(
            Horse horse,
            String currentCourse,
            String currentDistance) {

        double score = calculateScore(horse);

        List<PastRaceInfo> races = getRaceHistory(horse);

        for (int i = 0; i < races.size(); i++) {
            score += calculateCourseScore(currentCourse, races.get(i)) * WEIGHTS.get(i);
        }

        for (int i = 0; i < races.size(); i++) {
            score += calculateDistanceScore(currentDistance, races.get(i)) * WEIGHTS.get(i);
        }

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

    private double calculateCourseScore(String currentCourse, PastRaceInfo pastRace) {
        if (pastRace == null || pastRace.getRank() == 0) {
            return 0;
        }

        if (currentCourse == null || currentCourse.isBlank()) {
            return 0;
        }

        if (pastRace.getCourse() == null || pastRace.getCourse().isBlank()) {
            return 0;
        }

        if (currentCourse.equals(pastRace.getCourse()) && pastRace.getRank() <= 3) {
            return 3;
        } else if (!currentCourse.equals(pastRace.getCourse()) && pastRace.getRank() > 3) {
            return 3;
        }

        return 0;
    }
}
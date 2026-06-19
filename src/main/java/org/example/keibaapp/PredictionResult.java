package org.example.keibaapp;

public class PredictionResult {
    private Horse horse;
    private double score;

    public PredictionResult(Horse horse, double score) {
        this.horse = horse;
        this.score = score;
    }

    public Horse getHorse() {
        return horse;
    }

    public double getScore() {
        return score;
    }
}
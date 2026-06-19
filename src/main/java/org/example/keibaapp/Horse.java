package org.example.keibaapp;

public class Horse {
    private final String waku;
    private final String umaban;
    private final String name;
    private final String jockeyName;
    private final String jockeyWeight;
    private double odds;
    private double predictionScore;
    private String predictionReason;
    private String horseUrl;
    private PastRaceInfo lastRace;
    private PastRaceInfo secondLastRace;
    private PastRaceInfo thirdLastRace;
    private PastRaceInfo actualRace;
    private int actualRank;

    public int getActualRank() {
        return actualRank;
    }

    public void setActualRank(int actualRank) {
        this.actualRank = actualRank;
    }

    public PastRaceInfo getLastRace() {
        return lastRace;
    }

    public void setLastRace(PastRaceInfo lastRace) {
        this.lastRace = lastRace;
    }

    public PastRaceInfo getSecondLastRace() {
        return secondLastRace;
    }

    public void setSecondLastRace(PastRaceInfo secondLastRace) {
        this.secondLastRace = secondLastRace;
    }

    public PastRaceInfo getThirdLastRace() {
        return thirdLastRace;
    }

    public void setThirdLastRace(PastRaceInfo thirdLastRace) { this.thirdLastRace = thirdLastRace; }

    public PastRaceInfo getActualRace() {
        return actualRace;
    }

    public void setActualRace(PastRaceInfo actualRace) { this.actualRace = actualRace; }

    public String getHorseUrl() {
        return horseUrl;
    }

    public void setHorseUrl(String horseUrl) {
        this.horseUrl = horseUrl;
    }

    Horse(String waku, String umaban, String name, String jockeyName, String jockeyWeight, double odds) {
        this.waku = waku;
        this.umaban = umaban;
        this.name = name;
        this.jockeyName = jockeyName;
        this.jockeyWeight = jockeyWeight;
        this.odds = odds;
    }

    public String getWaku() {
        return waku;
    }

    public String getUmaban() {
        return umaban;
    }

    public String getName() {
        return name;
    }

    public String getJockeyName() {
        return jockeyName;
    }

    public String getJockeyWeight() {
        return jockeyWeight;
    }

    public double getOdds() {
        return odds;
    }

    public double getPredictionScore() {
        return predictionScore;
    }

    public void setPredictionScore(double predictionScore) {
        this.predictionScore = predictionScore;
    }

    public String getPredictionReason() {
        return predictionReason;
    }

    public void setPredictionReason(String predictionReason) {
        this.predictionReason = predictionReason;
    }
}
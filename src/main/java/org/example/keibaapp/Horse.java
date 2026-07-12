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
    private String jockeyUrl;
    private JockeyStats jockeyStats;
    private PastRaceInfo lastRace;
    private PastRaceInfo secondLastRace;
    private PastRaceInfo thirdLastRace;
    private PastRaceInfo actualRace;
    private int actualRank;
    private String aiPrompt;
    private String aiComment;
    private String sire;
    private String dam;
    private String damSire;

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

    public String getJockeyUrl() {
        return jockeyUrl;
    }

    public void setJockeyUrl(String jockeyUrl) {
        this.jockeyUrl = jockeyUrl;
    }

    // Yahoo!スポーツと同じJRA登録番号がnetkeibaのURLでもそのまま使えるため、
    // IDを抜き出してnetkeiba側のURLに組み替える
    public String getNetkeibaHorseUrl() {
        return toNetkeibaUrl(horseUrl, "horse");
    }

    public String getNetkeibaJockeyUrl() {
        return toNetkeibaUrl(jockeyUrl, "jockey");
    }

    private String toNetkeibaUrl(String yahooUrl, String type) {
        if (yahooUrl == null || yahooUrl.isEmpty()) {
            return "";
        }

        String trimmed = yahooUrl.endsWith("/")
                ? yahooUrl.substring(0, yahooUrl.length() - 1)
                : yahooUrl;

        String id = trimmed.substring(trimmed.lastIndexOf('/') + 1);

        if (id.isEmpty()) {
            return "";
        }

        return "https://db.netkeiba.com/" + type + "/" + id + "/";
    }

    public JockeyStats getJockeyStats() {
        return jockeyStats;
    }

    public void setJockeyStats(JockeyStats jockeyStats) {
        this.jockeyStats = jockeyStats;
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

    public String getAiPrompt() {
        return aiPrompt;
    }

    public void setAiPrompt(String aiPrompt) {
        this.aiPrompt = aiPrompt;
    }

    public String getAiComment() {
        return aiComment;
    }

    public void setAiComment(String aiComment) {
        this.aiComment = aiComment;
    }

    public String getSire() {
        return sire;
    }

    public void setSire(String sire) {
        this.sire = sire;
    }

    public String getDam() {
        return dam;
    }

    public void setDam(String dam) {
        this.dam = dam;
    }

    public String getDamSire() {
        return damSire;
    }

    public void setDamSire(String damSire) {
        this.damSire = damSire;
    }
}
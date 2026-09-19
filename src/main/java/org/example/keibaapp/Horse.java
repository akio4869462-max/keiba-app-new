package org.example.keibaapp;

public class Horse {
    private final String waku;
    private final String umaban;
    private final String name;
    private final String jockeyName;
    private final String jockeyWeight;
    private double odds;
    // モデル勝率(p_model)を百分率で表したもの(0〜100)。PredictionService.applyRaceModel参照
    private double predictionScore;
    private String predictionReason;
    // 単勝オッズから逆算した市場確率(q)を百分率で表したもの(0〜100)
    private double marketProbability;
    // 妙味 = predictionScore/100 / (marketProbability/100) - 1
    private double overlay;
    // このレース内でのオッズ順の人気(1が一番人気)。オッズ無効な馬は0
    private int popularity;
    // 妙味・人気帯の条件を満たす買い候補かどうか(参考値、断定的な推奨ではない)
    private boolean recommended;
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
    // 生産者名。市場相対残差(MODEL_REVISION.md §8)の照合キーとして使う。
    // 馬詳細ページ由来のため取得できるのはHorseEnrichmentService.fetchHorseDetail経由のみ
    private String breeder;

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

    public double getMarketProbability() {
        return marketProbability;
    }

    public void setMarketProbability(double marketProbability) {
        this.marketProbability = marketProbability;
    }

    public double getOverlay() {
        return overlay;
    }

    public void setOverlay(double overlay) {
        this.overlay = overlay;
    }

    public int getPopularity() {
        return popularity;
    }

    public void setPopularity(int popularity) {
        this.popularity = popularity;
    }

    public boolean isRecommended() {
        return recommended;
    }

    public void setRecommended(boolean recommended) {
        this.recommended = recommended;
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

    public String getBreeder() {
        return breeder;
    }

    public void setBreeder(String breeder) {
        this.breeder = breeder;
    }
}
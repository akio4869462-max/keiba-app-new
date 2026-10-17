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
    // 前走の走路バイアス特徴(MODEL_REVISION.md §3.1/§6。TrackBiasService参照)。
    // 前走が障害戦・地方/海外・統計が未取得のときはnullで、スコアへの寄与は0になる
    private Double pHwLat;
    private Double pLatC;
    // 複勝ギャップ(FukuGapCalculator、MODEL_REVISION.md §8.12)。発走10分前の再取得時だけ付与され、
    // それ以外(毎時の一覧取得など)はnullで、スコアへの寄与は0になる
    private Double fukuGap;
    // 発走10分前の馬連・ワイド・馬単の板から作った馬ごとのシェア(PoolShareCalculator)。
    // 前向きに記録して効果を確かめる段階で、まだ予想モデルには使わない
    private Double umarenShare;
    private Double wideShare;
    private Double exactaFirstShare;

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

    public Double getPHwLat() {
        return pHwLat;
    }

    public void setPHwLat(Double pHwLat) {
        this.pHwLat = pHwLat;
    }

    public Double getPLatC() {
        return pLatC;
    }

    public void setPLatC(Double pLatC) {
        this.pLatC = pLatC;
    }

    public Double getFukuGap() {
        return fukuGap;
    }

    public void setFukuGap(Double fukuGap) {
        this.fukuGap = fukuGap;
    }

    public Double getUmarenShare() {
        return umarenShare;
    }

    public void setUmarenShare(Double umarenShare) {
        this.umarenShare = umarenShare;
    }

    public Double getWideShare() {
        return wideShare;
    }

    public void setWideShare(Double wideShare) {
        this.wideShare = wideShare;
    }

    public Double getExactaFirstShare() {
        return exactaFirstShare;
    }

    public void setExactaFirstShare(Double exactaFirstShare) {
        this.exactaFirstShare = exactaFirstShare;
    }
}

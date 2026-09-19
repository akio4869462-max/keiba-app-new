package org.example.keibaapp;

public class PastRaceInfo {
    private final String raceName;
    private String course;
    private String distance;
    private int fieldSize;

    private final int rank;
    private final String grade;
    private final int popularity;
    // このレースのdenma/index相当のURL(通常は/race/index/{id}形式)。
    // p_lat_c(前走で外を回した度合い、MODEL_REVISION.md §6.1)を計算する際、
    // /race/result/{id}に変換して結果ページのコーナー通過順位を取得するために使う
    private String raceUrl;
    // このレースでの馬番。結果ページのコーナー通過順位(全馬の馬番で表記)から
    // 対象馬を特定するために使う
    private String umaban;

    public PastRaceInfo(String raceName, int rank, String grade, int popularity) {
        this.rank = rank;
        this.grade = grade;
        this.popularity = popularity;
        this.raceName = raceName;
    }

    public int getRank() {
        return rank;
    }

    public String getGrade() {
        return grade;
    }

    public int getPopularity() {
        return popularity;
    }

    public String getRaceName() {
        return raceName;
    }

    public String getDisplayText() {
        if (rank == 0) {
            return "データなし";
        }

        return raceName + " "
                + rank + "着 ("
                + popularity + "人気)";
    }

    public static PastRaceInfo empty() {
        return new PastRaceInfo("データなし",0, "データなし", 0);
    }

    public String getCourse() {
        return course;
    }

    public void setCourse(String course) {
        this.course = course;
    }

    public String getDistance() {
        return distance;
    }

    public void setDistance(String distance) {
        this.distance = distance;
    }

    public int getFieldSize() {
        return fieldSize;
    }

    public void setFieldSize(int fieldSize) {
        this.fieldSize = fieldSize;
    }

    public String getRaceUrl() {
        return raceUrl;
    }

    public void setRaceUrl(String raceUrl) {
        this.raceUrl = raceUrl;
    }

    public String getUmaban() {
        return umaban;
    }

    public void setUmaban(String umaban) {
        this.umaban = umaban;
    }
}
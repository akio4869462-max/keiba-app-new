package org.example.keibaapp;

public class PastRaceInfo {
    private final String raceName;
    private String course;
    private String distance;
    private int fieldSize;

    private final int rank;
    private final String grade;
    private final int popularity;

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

    public String getRaceName() { return raceName; }

    public String getDisplayText() {
        if (rank == 0) {
            return "データなし";
        }
        return raceName + " " + rank + "着 (" + popularity + "人気)";
    }

    public static PastRaceInfo empty() {
        return new PastRaceInfo("データなし",0, "データなし", 0);
    }
}
package org.example.keibaapp;

public class Horse {
    private String waku, umaban, name, jockeyName, jockeyWeight;
    private double odds;

    Horse(String waku, String umaban, String name, String jockeyName, String jockeyWeight, double odds) {
        this.waku = waku; this.umaban = umaban; this.name = name;
        this.jockeyName = jockeyName; this.jockeyWeight = jockeyWeight; this.odds = odds;
    }
    public String getWaku() { return waku; }
    public String getUmaban() { return umaban; }
    public String getName() { return name; }
    public String getJockeyName() { return jockeyName; }
    public String getJockeyWeight() { return jockeyWeight; }
    public double getOdds() { return odds; }
}

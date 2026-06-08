package org.example.keibaapp;

import java.util.List;
import java.time.LocalTime;

public class RaceInfo {
    int raceNum;
    String venue;
    String raceName;
    LocalTime raceTime;
    List<Horse> horses;

    RaceInfo(int raceNum, String venue, String raceName, LocalTime raceTime, List<Horse> horses) {
        this.raceNum = raceNum; this.raceName = raceName; this.venue = venue;
        this.raceTime = raceTime; this.horses = horses;
    }
    public String getRaceName() { return raceName; }
    public LocalTime getRaceTime() { return raceTime; }
    public List<Horse> getHorses() { return horses; }
}
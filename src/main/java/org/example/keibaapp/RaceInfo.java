package org.example.keibaapp;

import java.time.LocalTime;
import java.util.List;

public class RaceInfo {

    int raceNum;
    String venue;
    String raceName;
    LocalTime raceTime;
    List<Horse> horses;
    String course;
    String distance;

    RaceInfo(int raceNum,
             String venue,
             String raceName,
             LocalTime raceTime,
             String course,
             String distance,
             List<Horse> horses) {

        this.raceNum = raceNum;
        this.raceName = raceName;
        this.venue = venue;
        this.raceTime = raceTime;
        this.course = course;
        this.distance = distance;
        this.horses = horses;
    }

    public int getRaceNum() {
        return raceNum;
    }

    public String getVenue() {
        return venue;
    }

    public String getRaceName() {
        return raceName;
    }

    public LocalTime getRaceTime() {
        return raceTime;
    }

    public List<Horse> getHorses() {
        return horses;
    }

    public String getDisplayRaceName() {
        return venue + " " + raceNum + "R " + raceName;
    }

    public String getCourse() {
        return course;
    }

    public String getDistance() {
        return distance;
    }
}
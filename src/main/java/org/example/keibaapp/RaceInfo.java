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
    // 締切前オッズ再取得(RaceService.refreshOddsNearPost)のためのdenmaページURL。
    // 生成元によっては設定されないことがあるためnull許容(その場合は再取得対象外)
    String raceUrl;

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

    public void setHorses(List<Horse> horses) {
        this.horses = horses;
    }

    public String getRaceUrl() {
        return raceUrl;
    }

    public void setRaceUrl(String raceUrl) {
        this.raceUrl = raceUrl;
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
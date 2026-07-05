package org.example.keibaapp;

import java.time.LocalDate;
import java.util.List;

public class RaceResultGroup {
    private final LocalDate raceDate;
    private final String venue;
    private final int raceNumber;
    private final String raceName;
    private final List<RaceResultRecord> horses;

    public RaceResultGroup(
            LocalDate raceDate,
            String venue,
            int raceNumber,
            String raceName,
            List<RaceResultRecord> horses) {

        this.raceDate = raceDate;
        this.venue = venue;
        this.raceNumber = raceNumber;
        this.raceName = raceName;
        this.horses = horses;
    }

    public LocalDate getRaceDate() {
        return raceDate;
    }

    public String getVenue() {
        return venue;
    }

    public int getRaceNumber() {
        return raceNumber;
    }

    public String getRaceName() {
        return raceName;
    }

    public List<RaceResultRecord> getHorses() {
        return horses;
    }

    public String getDisplayName() {
        return venue + " " + raceNumber + "R " + raceName;
    }
}

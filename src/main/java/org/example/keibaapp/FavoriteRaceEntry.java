package org.example.keibaapp;

public class FavoriteRaceEntry {
    private final RaceInfo race;
    private final Horse horse;
    private final String matchedName;

    public FavoriteRaceEntry(RaceInfo race, Horse horse, String matchedName) {
        this.race = race;
        this.horse = horse;
        this.matchedName = matchedName;
    }

    public RaceInfo getRace() {
        return race;
    }

    public Horse getHorse() {
        return horse;
    }

    public String getMatchedName() {
        return matchedName;
    }
}

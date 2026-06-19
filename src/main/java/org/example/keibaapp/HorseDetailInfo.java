package org.example.keibaapp;

public class HorseDetailInfo {

    private PastRaceInfo lastRace;
    private PastRaceInfo secondLastRace;
    private PastRaceInfo thirdLastRace;
    private PastRaceInfo actualRace;

    public HorseDetailInfo(
            PastRaceInfo lastRace,
            PastRaceInfo secondLastRace,
            PastRaceInfo thirdLastRace,
            PastRaceInfo actualRace) {

        this.lastRace = lastRace;
        this.secondLastRace = secondLastRace;
        this.thirdLastRace = thirdLastRace;
        this.actualRace = actualRace;
    }

    public PastRaceInfo getLastRace() {
        return lastRace;
    }

    public PastRaceInfo getSecondLastRace() {
        return secondLastRace;
    }

    public PastRaceInfo getThirdLastRace() {
        return thirdLastRace;
    }

    public PastRaceInfo getActualRace() { return actualRace; }

    public static HorseDetailInfo empty() {
        return new HorseDetailInfo(
                PastRaceInfo.empty(),
                PastRaceInfo.empty(),
                PastRaceInfo.empty(),
                PastRaceInfo.empty()
        );
    }
}
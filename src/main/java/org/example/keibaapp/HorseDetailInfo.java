package org.example.keibaapp;

public class HorseDetailInfo {

    private PastRaceInfo lastRace;
    private PastRaceInfo secondLastRace;
    private PastRaceInfo thirdLastRace;
    private PastRaceInfo actualRace;
    private String breeder;

    public HorseDetailInfo(
            PastRaceInfo lastRace,
            PastRaceInfo secondLastRace,
            PastRaceInfo thirdLastRace,
            PastRaceInfo actualRace,
            String breeder) {

        this.lastRace = lastRace;
        this.secondLastRace = secondLastRace;
        this.thirdLastRace = thirdLastRace;
        this.actualRace = actualRace;
        this.breeder = breeder;
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

    public String getBreeder() {
        return breeder;
    }

    public static HorseDetailInfo empty() {
        return new HorseDetailInfo(
                PastRaceInfo.empty(),
                PastRaceInfo.empty(),
                PastRaceInfo.empty(),
                PastRaceInfo.empty(),
                ""
        );
    }
}

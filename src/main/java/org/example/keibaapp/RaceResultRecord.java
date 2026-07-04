package org.example.keibaapp;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
public class RaceResultRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate raceDate;
    private String venue;
    private int raceNumber;
    private String raceName;
    private String horseName;
    private double odds;
    private int predictionRank;
    private double predictionScore;
    private int actualRank;
    private LocalDateTime createdAt;

    public RaceResultRecord() {
    }

    public RaceResultRecord(
            LocalDate raceDate,
            String venue,
            int raceNumber,
            String raceName,
            String horseName,
            double odds,
            int predictionRank,
            double predictionScore,
            int actualRank) {

        this.raceDate = raceDate;
        this.venue = venue;
        this.raceNumber = raceNumber;
        this.raceName = raceName;
        this.horseName = horseName;
        this.odds = odds;
        this.predictionRank = predictionRank;
        this.predictionScore = predictionScore;
        this.actualRank = actualRank;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
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

    public String getHorseName() {
        return horseName;
    }

    public double getOdds() {
        return odds;
    }

    public int getPredictionRank() {
        return predictionRank;
    }

    public double getPredictionScore() {
        return predictionScore;
    }

    public int getActualRank() {
        return actualRank;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

package org.example.keibaapp;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
public class RacePayout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate raceDate;
    private String venue;
    private int raceNumber;
    private String betType;
    private String combination;
    private int payoutYen;

    public RacePayout() {
    }

    public RacePayout(
            LocalDate raceDate,
            String venue,
            int raceNumber,
            String betType,
            String combination,
            int payoutYen) {

        this.raceDate = raceDate;
        this.venue = venue;
        this.raceNumber = raceNumber;
        this.betType = betType;
        this.combination = combination;
        this.payoutYen = payoutYen;
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

    public String getBetType() {
        return betType;
    }

    public String getCombination() {
        return combination;
    }

    public int getPayoutYen() {
        return payoutYen;
    }
}

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
    // 妙味 = モデル勝率/市場確率 - 1(PredictionService参照)
    private double overlay;
    // このレース内でのオッズ順の人気(1が一番人気)
    private int popularity;
    // どの予想モデルで算出したかの識別子(PredictionService.MODEL_VERSION)。
    // モデルを改訂した際に過去データと混同せず比較できるようにするため
    private String modelVersion;

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
            int actualRank,
            double overlay,
            int popularity,
            String modelVersion) {

        this.raceDate = raceDate;
        this.venue = venue;
        this.raceNumber = raceNumber;
        this.raceName = raceName;
        this.horseName = horseName;
        this.odds = odds;
        this.predictionRank = predictionRank;
        this.predictionScore = predictionScore;
        this.actualRank = actualRank;
        this.overlay = overlay;
        this.popularity = popularity;
        this.modelVersion = modelVersion;
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

    public double getOverlay() {
        return overlay;
    }

    public int getPopularity() {
        return popularity;
    }

    public String getModelVersion() {
        return modelVersion;
    }
}

package org.example.keibaapp;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
public class TrackedRaceUrl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String raceUrl;

    private LocalDate raceDate;

    private boolean processed;

    public TrackedRaceUrl() {
    }

    public TrackedRaceUrl(String raceUrl, LocalDate raceDate) {
        this.raceUrl = raceUrl;
        this.raceDate = raceDate;
        this.processed = false;
    }

    public Long getId() {
        return id;
    }

    public String getRaceUrl() {
        return raceUrl;
    }

    public LocalDate getRaceDate() {
        return raceDate;
    }

    public boolean isProcessed() {
        return processed;
    }

    public void setProcessed(boolean processed) {
        this.processed = processed;
    }
}

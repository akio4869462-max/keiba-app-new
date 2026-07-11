package org.example.keibaapp;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
public class NotificationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String horseName;

    private String raceName;

    // "サラ系2歳未勝利"等のレース名は毎週使い回されるため、日付がないと
    // 別の週の同名レースを「通知済み」と誤判定してしまう
    private LocalDate raceDate;

    public NotificationHistory() {
    }

    public NotificationHistory(
            String horseName,
            String raceName,
            LocalDate raceDate) {

        this.horseName = horseName;
        this.raceName = raceName;
        this.raceDate = raceDate;
    }

    public Long getId() {
        return id;
    }

    public String getHorseName() {
        return horseName;
    }

    public String getRaceName() {
        return raceName;
    }

    public LocalDate getRaceDate() {
        return raceDate;
    }
}
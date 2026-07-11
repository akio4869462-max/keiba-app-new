package org.example.keibaapp;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
public class NotificationJockeyHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String jockeyName;

    private String raceName;

    // "サラ系2歳未勝利"等のレース名は毎週使い回されるため、日付がないと
    // 別の週の同名レースを「通知済み」と誤判定してしまう
    private LocalDate raceDate;

    public NotificationJockeyHistory() {
    }

    public NotificationJockeyHistory(
            String jockeyName,
            String raceName,
            LocalDate raceDate) {

        this.jockeyName = jockeyName;
        this.raceName = raceName;
        this.raceDate = raceDate;
    }

    public Long getId() {
        return id;
    }

    public String getJockeyName() {
        return jockeyName;
    }

    public String getRaceName() {
        return raceName;
    }

    public LocalDate getRaceDate() {
        return raceDate;
    }
}
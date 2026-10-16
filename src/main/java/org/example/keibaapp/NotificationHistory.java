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

    // 同じ日に同名のレース("サラ系2歳未勝利"等)が複数あるため、レース名だけでは
    // 「通知済み」の判定が別のレースとぶつかる。開催場とレース番号で特定する。
    // 記録し始める前の旧レコードはnull(NOT NULL制約でALTER TABLEが失敗しないようboxed型)
    private String venue;

    private Integer raceNumber;

    // "サラ系2歳未勝利"等のレース名は毎週使い回されるため、日付がないと
    // 別の週の同名レースを「通知済み」と誤判定してしまう
    private LocalDate raceDate;

    public NotificationHistory() {
    }

    public NotificationHistory(
            String horseName,
            String raceName,
            String venue,
            int raceNumber,
            LocalDate raceDate) {

        this.horseName = horseName;
        this.raceName = raceName;
        this.venue = venue;
        this.raceNumber = raceNumber;
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

    public String getVenue() {
        return venue;
    }

    public Integer getRaceNumber() {
        return raceNumber;
    }

    public LocalDate getRaceDate() {
        return raceDate;
    }
}
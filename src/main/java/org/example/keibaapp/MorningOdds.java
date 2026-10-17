package org.example.keibaapp;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

// レース当日の朝(開催日の早い時間帯)に取得した単勝オッズ。発走10分前のオッズとの差(オッズの動き)を
// 特徴にするための記録で、学習データ(MODEL_REVISION.md §8.12)の「朝」に対応する。
// minutesToPost(取得時点から発走まで何分あったか)を残し、発走直前に取った分を朝として扱わないようにできる。
// 新規テーブルだが、将来の列追加で既存行のALTERが失敗しないようプリミティブではなくnull許容の型を使う
@Entity
public class MorningOdds {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate raceDate;
    private String venue;
    private Integer raceNumber;
    private Integer umaban;
    private Double winOdds;
    private Integer minutesToPost;
    private LocalDateTime capturedAt;

    public MorningOdds() {
    }

    public MorningOdds(LocalDate raceDate, String venue, int raceNumber, int umaban, double winOdds,
                       int minutesToPost, LocalDateTime capturedAt) {
        this.raceDate = raceDate;
        this.venue = venue;
        this.raceNumber = raceNumber;
        this.umaban = umaban;
        this.winOdds = winOdds;
        this.minutesToPost = minutesToPost;
        this.capturedAt = capturedAt;
    }

    public Long getId() { return id; }
    public LocalDate getRaceDate() { return raceDate; }
    public String getVenue() { return venue; }
    public Integer getRaceNumber() { return raceNumber; }
    public Integer getUmaban() { return umaban; }
    public Double getWinOdds() { return winOdds; }
    public Integer getMinutesToPost() { return minutesToPost; }
    public LocalDateTime getCapturedAt() { return capturedAt; }
}

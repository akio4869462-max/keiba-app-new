package org.example.keibaapp;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 発走10分前の再取得時点の予想(オッズ・複勝ギャップ・モデル勝率・妙味・推奨フラグ)を馬ごとに残したもの。
// 結果収集が記録するRaceResultRecordは確定後のページ由来で、買う時点(10分前)の予想ではないため、
// 「10分前のオッズで買って確定オッズで受け取る」前向きの成績(ForwardEvaluationService)は
// こちらを使って評価する。新規テーブルだが、将来の列追加で既存行のALTERが失敗しないよう
// プリミティブではなくnull許容の型を使う
@Entity
public class OddsSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate raceDate;
    private String venue;
    private Integer raceNumber;
    private Integer umaban;
    private String horseName;
    // 10分前時点の単勝オッズ
    private Double winOdds;
    // 直前の一覧取得(通常は最大1時間前)時点の単勝オッズ。朝オッズの代わりの参考値
    private Double earlierWinOdds;
    private Double fukuGap;
    // モデル勝率・市場確率(いずれも百分率)
    private Double winProbability;
    private Double marketProbability;
    private Double overlay;
    private Integer popularity;
    private Boolean recommended;
    private String modelVersion;
    private LocalDateTime capturedAt;

    public OddsSnapshot() {
    }

    public OddsSnapshot(LocalDate raceDate, String venue, int raceNumber, int umaban, String horseName,
                        double winOdds, Double earlierWinOdds, Double fukuGap, double winProbability,
                        double marketProbability, double overlay, int popularity, boolean recommended,
                        String modelVersion, LocalDateTime capturedAt) {
        this.raceDate = raceDate;
        this.venue = venue;
        this.raceNumber = raceNumber;
        this.umaban = umaban;
        this.horseName = horseName;
        this.winOdds = winOdds;
        this.earlierWinOdds = earlierWinOdds;
        this.fukuGap = fukuGap;
        this.winProbability = winProbability;
        this.marketProbability = marketProbability;
        this.overlay = overlay;
        this.popularity = popularity;
        this.recommended = recommended;
        this.modelVersion = modelVersion;
        this.capturedAt = capturedAt;
    }

    public Long getId() { return id; }
    public LocalDate getRaceDate() { return raceDate; }
    public String getVenue() { return venue; }
    public Integer getRaceNumber() { return raceNumber; }
    public Integer getUmaban() { return umaban; }
    public String getHorseName() { return horseName; }
    public Double getWinOdds() { return winOdds; }
    public Double getEarlierWinOdds() { return earlierWinOdds; }
    public Double getFukuGap() { return fukuGap; }
    public Double getWinProbability() { return winProbability; }
    public Double getMarketProbability() { return marketProbability; }
    public Double getOverlay() { return overlay; }
    public Integer getPopularity() { return popularity; }
    public Boolean getRecommended() { return recommended; }
    public String getModelVersion() { return modelVersion; }
    public LocalDateTime getCapturedAt() { return capturedAt; }
}

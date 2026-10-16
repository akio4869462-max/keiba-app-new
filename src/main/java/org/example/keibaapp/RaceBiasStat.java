package org.example.keibaapp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

// 終了済みレース1件ぶんの走路バイアス統計(TrackBiasService参照)。
// 過去のレース結果は不変なので一度計算したら永続化して再取得しない。
// bm/bmSeはバイアスが推定できなかった(頭数不足・障害戦など)場合はnull。
// 新規テーブルだが、他のエンティティ同様、将来カラムを足すときに本番の既存行で
// ALTERが失敗しないよう、プリミティブではなくnull許容の型を使う
@Entity
public class RaceBiasStat {

    @Id
    private String raceId;

    private LocalDate raceDate;
    private String venue;
    // "芝" / "ダ" / "障害"
    private String surface;
    private Double bm;
    private Double bmSe;
    private Double meanLat;

    // 馬番ごとの平均lat。"1:0.5;2:1.25" 形式
    @Column(length = 2000)
    private String latMeans;

    public RaceBiasStat() {
    }

    public RaceBiasStat(String raceId, LocalDate raceDate, String venue, String surface,
                        Double bm, Double bmSe, Double meanLat, Map<Integer, Double> latMeans) {
        this.raceId = raceId;
        this.raceDate = raceDate;
        this.venue = venue;
        this.surface = surface;
        this.bm = bm;
        this.bmSe = bmSe;
        this.meanLat = meanLat;
        this.latMeans = encode(latMeans);
    }

    public String getRaceId() {
        return raceId;
    }

    public LocalDate getRaceDate() {
        return raceDate;
    }

    public String getVenue() {
        return venue;
    }

    public String getSurface() {
        return surface;
    }

    public Double getBm() {
        return bm;
    }

    public Double getBmSe() {
        return bmSe;
    }

    public Double getMeanLat() {
        return meanLat;
    }

    public Map<Integer, Double> getLatMeans() {
        return decode(latMeans);
    }

    TrackBiasCalculator.RaceBias toRaceBias() {
        return new TrackBiasCalculator.RaceBias(bm, bmSe, meanLat);
    }

    static String encode(Map<Integer, Double> latMeans) {
        StringBuilder sb = new StringBuilder();

        for (Map.Entry<Integer, Double> entry : new java.util.TreeMap<>(latMeans).entrySet()) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(entry.getKey()).append(':').append(entry.getValue());
        }

        return sb.toString();
    }

    static Map<Integer, Double> decode(String text) {
        Map<Integer, Double> map = new HashMap<>();

        if (text == null || text.isBlank()) {
            return map;
        }

        for (String pair : text.split(";")) {
            String[] kv = pair.split(":");

            if (kv.length == 2) {
                map.put(Integer.parseInt(kv[0]), Double.parseDouble(kv[1]));
            }
        }

        return map;
    }
}

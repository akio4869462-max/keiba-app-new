package org.example.keibaapp;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// 結果ページ(/race/result/{id})から走路バイアスの計算に必要な項目だけを取り出したもの。
// surfaceは"芝" / "ダ" / "障害"のいずれか(障害は走路バイアスの対象外)。
// rankは数字で着順が付いた馬のみ1以上、中止・失格等は0
public record ResultPageInfo(
        LocalDate date,
        String venue,
        String surface,
        List<ResultRow> rows,
        Map<Integer, Double> latMeans) {

    public record ResultRow(int umaban, int rank, double odds) {
    }
}

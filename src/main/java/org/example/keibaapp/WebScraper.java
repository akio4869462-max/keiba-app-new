package org.example.keibaapp;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;

public class WebScraper {

    // 指定されたURLからHTMLを取得するメソッド（Yahoo!競馬用につなぎます）
    public static Document getHTML(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .get();
    }

    // レース名を取得するメソッド（Yahoo!競馬のタイトル部分を狙います）
    public static String getRaceName(Document doc) {
        // クラス名「hr-predictRaceInfo__title」を持つ要素を探す
        Element raceTitleElement = doc.selectFirst(".hr-predictRaceInfo__title");

        if (raceTitleElement != null) {
            // text() で中の文字を抜き出し、余計な空白を消す
            return raceTitleElement.text().trim();
        }
        return "レース名取得不可";
    }

    // 出馬表の「各馬のデータ行（tr）」を丸ごと取得するメソッド
    public static Elements getRaceRows(Document doc) {
        // Yahoo!競馬の出馬表テーブルの中にある、お馬さんごとの行（tr）を全件引っ張ってきます
        return doc.select(".kb-denmaTable tbody tr");
    }
    public static String getRaceTime(Document doc) {
        // 日付関連のエリアを取得
        Element dateArea = doc.selectFirst(".hr-predictRaceInfo__date");
        if (dateArea != null) {
            // 同じクラス名の要素をすべてリストにする
            Elements texts = dateArea.getElementsByClass("hr-predictRaceInfo__text");

            // 3番目（インデックス2）が発走時刻のはずです
            if (texts.size() >= 3) {
                String rawTime = texts.get(2).text(); // "10:05発走" が取れる
                return rawTime.replace("発走", "").trim(); // "10:05" に加工
            }
        }
        return "00:00";
    }
}
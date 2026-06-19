package org.example.keibaapp;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;

public class WebScraper {

    // 指定されたURLからHTMLを取得するメソッド（Yahoo!競馬用につなぎます）
    public static Document getHTML(String url) throws IOException {
        IOException lastException = null;

        for (int i = 0; i < 3; i++) {
            try {
                return Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Safari/537.36")
                        .referrer("https://sports.yahoo.co.jp/")
                        .header("Accept-Language", "ja,en-US;q=0.9,en;q=0.8")
                        .timeout(15000)
                        .get();
            } catch (IOException e) {
                lastException = e;

                System.out.println("取得失敗(" + (i + 1) + "/3): " + url + " / " + e.getMessage());

                try {
                    Thread.sleep(5000);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }

        throw lastException;
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

    public static void debugHorsePage(String horseUrl) {
        try {
            Document doc = getHTML(horseUrl);

            System.out.println("馬詳細タイトル: " + doc.title());

            Elements tables = doc.select("table");
            System.out.println("テーブル数: " + tables.size());

            for (int i = 0; i < tables.size(); i++) {
                System.out.println("===== table " + i + " =====");
                System.out.println(tables.get(i).text());
            }

        } catch (Exception e) {
            System.out.println("馬詳細ページ取得失敗: " + e.getMessage());
        }
    }

    public static HorseDetailInfo getHorseDetailInfo(String horseUrl) {
        try {
            Document doc = getHTML(horseUrl);

            Elements tables = doc.select("table");

            if (tables.size() < 5) {
                return HorseDetailInfo.empty();
            }

            Element resultTable = tables.get(4);
            Elements rows = resultTable.select("tr");

            PastRaceInfo lastRace = parsePastRace(rows, 2);
            PastRaceInfo secondLastRace = parsePastRace(rows, 3);
            PastRaceInfo thirdLastRace = parsePastRace(rows, 4);
            PastRaceInfo actualRace = parsePastRace(rows, 1);

            return new HorseDetailInfo(
                    lastRace,
                    secondLastRace,
                    thirdLastRace,
                    actualRace
            );

        } catch (Exception e) {
            System.out.println("馬詳細情報取得失敗: " + horseUrl + " / " + e.getMessage());
            return HorseDetailInfo.empty();
        }
    }

    private static PastRaceInfo parsePastRace(Elements rows, int rowIndex) {
        if (rows.size() <= rowIndex) {
            return PastRaceInfo.empty();
        }

        Elements tds = rows.get(rowIndex).select("td");

        if (tds.size() < 6) {
            return PastRaceInfo.empty();
        }

        int rank = 0;
        String rankText = tds.get(2).text().trim();

        if (rankText.matches("\\d+")) {
            rank = Integer.parseInt(rankText);
        }

        String raceText = tds.get(1).text();
        String raceName = raceText;

        String grade;
        if (raceText.contains("GIII")) {
            grade = "GIII";
        } else if (raceText.contains("GII")) {
            grade = "GII";
        } else if (raceText.contains("GI")) {
            grade = "GI";
        } else if (raceText.contains("L")) {
            grade = "L";
        } else if (raceText.contains("OP")) {
            grade = "OP";
        } else {
            grade = "条件戦";
        }

        int popularity = 0;
        String popularityText = tds.get(5).text();
        String popularityValue = popularityText.split("\\s+")[0];

        if (popularityValue.matches("\\d+")) {
            popularity = Integer.parseInt(popularityValue);
        }

        return new PastRaceInfo(raceName, rank, grade, popularity);
    }
}
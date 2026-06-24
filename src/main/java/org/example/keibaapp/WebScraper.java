package org.example.keibaapp;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;

public class WebScraper {
    private static final int PAST_RACE_TABLE_INDEX = 4;
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

    /**
     * Debug用
     */
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

    public static HorseDetailInfo getTodayHorseDetailInfo(String horseUrl) {
        try {
            Elements rows = getHorseResultRows(horseUrl);

            if (rows.isEmpty()) {
                return HorseDetailInfo.empty();
            }

            return new HorseDetailInfo(
                    parsePastRace(rows, 1),
                    parsePastRace(rows, 2),
                    parsePastRace(rows, 3),
                    PastRaceInfo.empty()
            );

        } catch (Exception e) {
            System.out.println("馬詳細情報取得失敗: " + horseUrl + " / " + e.getMessage());
            return HorseDetailInfo.empty();
        }
    }

    public static HorseDetailInfo getHistoricalHorseDetailInfo(String horseUrl) {
        try {
            Elements rows = getHorseResultRows(horseUrl);

            if (rows.isEmpty()) {
                return HorseDetailInfo.empty();
            }

            return new HorseDetailInfo(
                    parsePastRace(rows, 2),
                    parsePastRace(rows, 3),
                    parsePastRace(rows, 4),
                    parsePastRace(rows, 1)
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

        String grade = extractGrade(raceText);

        int popularity = 0;
        String popularityText = tds.get(5).text();
        String popularityValue = popularityText.split("\\s+")[0];

        if (popularityValue.matches("\\d+")) {
            popularity = Integer.parseInt(popularityValue);
        }

        PastRaceInfo pastRace =
                new PastRaceInfo(raceName, rank, grade, popularity);

        pastRace.setDistance(extractDistance(raceText));
        pastRace.setCourse(extractCourse(raceText));

        return pastRace;
    }

    private static Elements getHorseResultRows(String horseUrl) throws IOException {
        Document doc = getHTML(horseUrl);

        Elements tables = doc.select("table");

        if (tables.size() < 5) {
            return new Elements();
        }

        return tables.get(PAST_RACE_TABLE_INDEX).select("tr");
    }

    private static String extractGrade(String raceText) {
        if (raceText.contains("GIII")) {
            return "GIII";
        }
        if (raceText.contains("GII")) {
            return "GII";
        }
        if (raceText.contains("GI")) {
            return "GI";
        }
        if (raceText.contains("L")) {
            return "L";
        }
        if (raceText.contains("OP")) {
            return "OP";
        }
        return "条件戦";
    }

    private static String extractDistance(String raceText) {
        java.util.regex.Pattern pattern =
                java.util.regex.Pattern.compile("(\\d{3,4})m");

        java.util.regex.Matcher matcher =
                pattern.matcher(raceText);

        if (matcher.find()) {
            return matcher.group(1) + "m";
        }

        return "";
    }

    private static String extractCourse(String raceText) {
        if (raceText.contains("芝")) {
            return "芝";
        }

        if (raceText.contains("ダ")) {
            return "ダ";
        }

        return "";
    }

    public static String getRaceDistance(Document doc) {
        Element dateArea = doc.selectFirst(".hr-predictRaceInfo__date");

        if (dateArea != null) {
            String text = dateArea.text();

            java.util.regex.Pattern pattern =
                    java.util.regex.Pattern.compile("(\\d{3,4})m");

            java.util.regex.Matcher matcher =
                    pattern.matcher(text);

            if (matcher.find()) {
                return matcher.group(1) + "m";
            }
        }

        return "";
    }

    public static String getRaceCourse(Document doc) {
        Element dateArea = doc.selectFirst(".hr-predictRaceInfo__date");

        if (dateArea != null) {
            String text = dateArea.text();

            if (text.contains("芝")) {
                return "芝";
            }

            if (text.contains("ダ")) {
                return "ダ";
            }
        }

        return "";
    }
}
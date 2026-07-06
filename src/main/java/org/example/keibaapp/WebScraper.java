package org.example.keibaapp;

import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class WebScraper {
    private static final int PAST_RACE_TABLE_INDEX = 4;
    private static final Pattern DISTANCE_PATTERN = Pattern.compile("(\\d{3,4})m");
    private static final Pattern FIELD_SIZE_PATTERN = Pattern.compile("(\\d+)頭");
    private static final List<String> GRADES = List.of(
            "GIII", "GII", "GI", "L", "OP",
            "3勝クラス", "2勝クラス", "1勝クラス", "未勝利", "新馬"
    );

    // サーキットブレーカー: 直近で取得に失敗し続けている間は、ブロック/障害中の
    // サイトに何度もアクセスしてしまわないよう、一定時間スクレイピング自体を
    // スキップする。定期実行・手動実行・ページ表示のどこから呼ばれても共通で効く
    private static volatile long circuitOpenUntil = 0;
    private static final long CIRCUIT_COOLDOWN_MS = 5 * 60 * 1000;

    // テストで状態をリセット・確認するためだけに用意（本番コードからは呼ばない）
    static void resetCircuitBreakerForTesting() {
        circuitOpenUntil = 0;
    }

    static boolean isCircuitOpenForTesting() {
        return System.currentTimeMillis() < circuitOpenUntil;
    }

    // 指定されたURLからHTMLを取得するメソッド（Yahoo!競馬用につなぎます）
    public static Document getHTML(String url) throws IOException {
        long now = System.currentTimeMillis();

        if (now < circuitOpenUntil) {
            throw new IOException(
                    "直近の取得失敗が続いているため一時停止中です（あと"
                            + ((circuitOpenUntil - now) / 1000) + "秒）: " + url);
        }

        IOException lastException = null;

        for (int i = 0; i < 3; i++) {
            try {
                Document doc = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Safari/537.36")
                        .referrer("https://sports.yahoo.co.jp/")
                        .header("Accept-Language", "ja,en-US;q=0.9,en;q=0.8")
                        .timeout(15000)
                        .get();

                circuitOpenUntil = 0;
                return doc;
            } catch (HttpStatusException e) {
                // 404/403などのクライアントエラーは、このURL固有の問題（存在しない
                // 馬・騎手ページ等）であり、サイト全体の障害を示すものではない。
                // リトライしても結果は変わらないため即座に諦め、サーキットブレーカー
                // にもカウントしない（無関係な処理まで巻き込んで止めてしまうため）
                if (e.getStatusCode() >= 400 && e.getStatusCode() < 500) {
                    System.out.println("取得失敗(対象なし): " + url + " / " + e.getMessage());
                    throw e;
                }

                lastException = e;
                System.out.println("取得失敗(" + (i + 1) + "/3): " + url + " / " + e.getMessage());

                try {
                    Thread.sleep(5000);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
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

        circuitOpenUntil = System.currentTimeMillis() + CIRCUIT_COOLDOWN_MS;
        System.out.println(
                "連続して取得に失敗したため" + (CIRCUIT_COOLDOWN_MS / 60000) + "分間スクレイピングを一時停止します");

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
        pastRace.setFieldSize(extractFieldSize(raceText));

        return pastRace;
    }

    public static JockeyStats getJockeyStats(String jockeyUrl) {
        try {
            Document doc = getHTML(jockeyUrl);

            Element table = doc.selectFirst("div.hr-jockeyReward table.hr-table");

            if (table == null) {
                return JockeyStats.empty();
            }

            Elements rows = table.select("tbody tr");

            if (rows.isEmpty()) {
                return JockeyStats.empty();
            }

            Elements tds = rows.get(0).select("td");

            if (tds.size() < 8) {
                return JockeyStats.empty();
            }

            double winRate = parseRate(tds.get(6).text().trim());
            double rentaiRate = parseRate(tds.get(7).text().trim());

            return new JockeyStats(winRate, rentaiRate);

        } catch (Exception e) {
            System.out.println("騎手成績取得失敗: " + jockeyUrl + " / " + e.getMessage());
            return JockeyStats.empty();
        }
    }

    private static double parseRate(String rateText) {
        if (rateText.isEmpty() || rateText.equals("-")) {
            return 0;
        }

        try {
            return Double.parseDouble(rateText);
        } catch (NumberFormatException e) {
            return 0;
        }
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
        for (String grade : GRADES) {
            if (raceText.contains(grade)) {
                return grade;
            }
        }
        return "条件戦";
    }

    private static String extractDistance(String raceText) {
        Matcher matcher = DISTANCE_PATTERN.matcher(raceText);

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

    private static int extractFieldSize(String raceText) {
        Matcher matcher = FIELD_SIZE_PATTERN.matcher(raceText);

        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }

        return 0;
    }

    public static String getRaceDistance(Document doc) {
        Element raceInfo = doc.selectFirst(".hr-predictRaceInfo");

        if (raceInfo != null) {
            String text = raceInfo.text();

            Matcher matcher = DISTANCE_PATTERN.matcher(text);

            if (matcher.find()) {
                return matcher.group(1) + "m";
            }
        }

        return "";
    }

    public static String getRaceCourse(Document doc) {
        Element raceInfo = doc.selectFirst(".hr-predictRaceInfo");

        if (raceInfo != null) {
            String text = raceInfo.text();

            if (text.contains("芝")) {
                return "芝";
            }

            if (text.contains("ダート")) {
                return "ダート";
            }
        }

        return "";
    }

    // 結果ページ(/race/result/{id})の払戻金テーブルを解析する。
    // 複勝・ワイドは1レースに3組み合わせあり、最初の行にだけ<th>(rowspan)が
    // 付くため、<th>が無い行は直前の馬券種を引き継ぐ
    public static List<PayoutEntry> getPayouts(Document doc) {
        List<PayoutEntry> payouts = new ArrayList<>();
        Elements tables = doc.select("table.hr-tableLeftTop");
        String currentBetType = null;

        for (Element table : tables) {
            for (Element row : table.select("tbody tr")) {
                Element betTypeHeader = row.selectFirst("th");

                if (betTypeHeader != null) {
                    currentBetType = betTypeHeader.text().trim();
                }

                Elements tds = row.select("td");

                if (currentBetType == null || tds.size() < 2) {
                    continue;
                }

                String combination = tds.get(0).text().trim();
                int payoutYen = parsePayoutYen(tds.get(1).text().trim());

                payouts.add(new PayoutEntry(currentBetType, combination, payoutYen));
            }
        }

        return payouts;
    }

    private static int parsePayoutYen(String payoutText) {
        String digitsOnly = payoutText.replaceAll("[^0-9]", "");

        if (digitsOnly.isEmpty()) {
            return 0;
        }

        return Integer.parseInt(digitsOnly);
    }

    public static String getVenueName(Document doc) {
        // 同じ日の全開催場を並べたナビゲーションのうち、このレースの開催場だけが
        // hr-menuWhite__item--current になっているので、そこから取得する
        // （--currentを付けないと常に先頭の開催場が返ってしまう）
        Element venueLink = doc.selectFirst(
                "li.hr-menuWhite__item--current a.hr-menuWhite__text[href*=/keiba/race/list/]");

        return venueLink != null ? venueLink.text().trim() : "";
    }
}
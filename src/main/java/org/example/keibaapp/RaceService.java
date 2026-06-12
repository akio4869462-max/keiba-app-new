package org.example.keibaapp;

import org.springframework.stereotype.Service;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.Comparator;
import java.time.LocalTime;
import java.time.LocalDateTime;

@Service
public class RaceService {
    private List<RaceInfo> cachedRaces;
    private LocalDateTime lastFetchedAt;
    public List<RaceInfo> getRaces() {
        System.out.println("★ServiceのgetRacesが呼ばれました！");

        if (cachedRaces != null
                && lastFetchedAt != null
                && lastFetchedAt.plusMinutes(30).isAfter(LocalDateTime.now())) {

            System.out.println("キャッシュを使用します");
            return cachedRaces;
        }

        System.out.println("最新データを取得します");

        cachedRaces = fetchAllRaces();
        lastFetchedAt = LocalDateTime.now();

        return cachedRaces;
    }

    private List<RaceInfo> createDummyRaces() {
        List<Horse> horses = new ArrayList<>();

        horses.add(new Horse("1", "1", "ダノンデサイル", "戸崎 圭太", "58.0", 999.9));
        horses.add(new Horse("1", "2", "ミュージアムマイル", "D.レーン", "54.0", 999.9));
        horses.add(new Horse("2", "3", "シュガークン", "吉村 誠之助", "58.0", 999.9));
        horses.add(new Horse("2", "4", "ミクニインスパイア", "丹内 祐次", "54.0", 999.9));
        horses.add(new Horse("3", "5", "クロワデュノール", "北村 友一", "54.0", 999.9));
        horses.add(new Horse("3", "6", "ビザンチンドリーム", "西村 淳也", "58.0", 999.9));
        horses.add(new Horse("4", "7", "ファミリータイム", "幸 英明", "58.0", 999.9));
        horses.add(new Horse("4", "8", "タガノデュード", "高杉 吏麒", "58.0", 999.9));
        horses.add(new Horse("5", "9", "コスモキュランダ", "横山 武史", "58.0", 999.9));
        horses.add(new Horse("5", "10", "ジューンテイク", "松山 弘平", "58.0", 999.9));
        horses.add(new Horse("6", "11", "シンエンペラー", "坂井 瑠星", "58.0", 999.9));
        horses.add(new Horse("6", "12", "マイネルエンペラー", "川田 将雅", "58.0", 999.9));
        horses.add(new Horse("7", "13", "シェイクユアハート", "古川 吉洋", "58.0", 999.9));
        horses.add(new Horse("7", "14", "スティンガーグラス", "岩田 望来", "58.0", 999.9));
        horses.add(new Horse("7", "15", "マイユニバース", "横山 典弘", "54.0", 999.9));
        horses.add(new Horse("8", "16", "メイショウタバル", "武 豊", "58.0", 999.9));
        horses.add(new Horse("8", "17", "レガレイラ", "C.ルメール", "56.0", 999.9));
        horses.add(new Horse("8", "18", "ミステリーウェイ", "松本 大輝", "58.0", 999.9));

        List<RaceInfo> races = new ArrayList<>();

        races.add(new RaceInfo(
                11,
                "3回阪神4日",
                "宝塚記念 GI",
                LocalTime.of(15, 40),
                horses
        ));

        return races;
    }

    // ...ここに fetchAllRaces() や WebScraper クラスの中身を移植
    public List<RaceInfo> fetchAllRaces() {
        List<RaceInfo> allRaces = new ArrayList<>();
        System.out.println("★デバッグ: fetchAllRacesが起動しました");

        // --- 強制テストここから ---
//        try {
//            Document testDoc = WebScraper.getHTML("https://sports.yahoo.co.jp/keiba/");
//            System.out.println("★デバッグ: タイトルは " + testDoc.title());
//        } catch (Exception e) {
//            System.out.println("★デバッグ: そもそもHTMLが取れていません！");
//        }

        // 1. トップページから開催場リストを取得
        try{
            Document topDoc = WebScraper.getHTML("https://sports.yahoo.co.jp/keiba/");
//            System.out.println("listリンク数 = " + topDoc.select("a[href*=/keiba/race/list/]").size());
//
//            for (Element link : topDoc.select("a[href*=/keiba/race/list/]")) {
//                System.out.println("候補URL: " + link.attr("abs:href"));
//                System.out.println("候補テキスト: " + link.text());
//            }
            Elements raceListLinks = topDoc.select("a[href*=/keiba/race/list/]");
            System.out.println("開催場数 = " + raceListLinks.size());

            for (Element link : raceListLinks) {
                String listUrl = link.attr("abs:href"); // 例: https://sports.yahoo.co.jp/keiba/race/list/26050302
//                System.out.println("開催場を発見: " + listUrl);

                for (int i = 1; i <= 12; i++) {
                    try {
                        // ここでURLを生成
                        String raceUrl = listUrl.replace("/list/", "/denma/") + String.format("%02d", i);
                        String venueName = link.text();

                        // ★修正箇所: targetUrl を raceUrl に変更
                        Document doc = WebScraper.getHTML(raceUrl);

                        String raceName = WebScraper.getRaceName(doc);
                        String rawTime = WebScraper.getRaceTime(doc); // "9:45" や "10:00"
                        if (rawTime.length() == 4) { // "9:45" のようなケース
                            rawTime = "0" + rawTime;
                        }
                        LocalTime raceTime = LocalTime.parse(rawTime);

                        List<Horse> horseList = new ArrayList<>();
                        Elements rows = doc.select(".hr-table tbody tr").isEmpty() ? doc.select("table tr") : doc.select(".hr-table tbody tr");

                        for (Element row : rows) {
                            if (row.selectFirst("th") != null || row.text().contains("枠番")) continue;
                            Elements tds = row.select("td");
                            if (tds.size() < 8) continue;

                            String waku = tds.get(0).text().trim();
                            String umaban = tds.get(1).text().trim();
                            String name = tds.get(2).text().split("\\s+")[0];
                            String jockeyData = tds.get(3).text().trim();

                            // ジョッキー名と体重の分離（念のためインデックスチェックを入れるとより安全です）
                            String jockeyName = jockeyData.substring(0, jockeyData.lastIndexOf(' ')).trim();
                            String weight = jockeyData.substring(jockeyData.lastIndexOf(' ') + 1);

                            // オッズ取得（素晴らしいロジックです！）
                            String rawOdds = tds.get(7).text().trim();
                            double oddsValue = 999.9;
                            Pattern pattern = Pattern.compile("\\(([^)]+)\\)");
                            Matcher matcher = pattern.matcher(rawOdds);

                            try {
                                if (matcher.find()) {
                                    String match = matcher.group(1);
                                    if (!match.equals("****")) {
                                        oddsValue = Double.parseDouble(match);
                                    }
                                } else {
                                    String cleanNum = rawOdds.replaceAll("[^0-9.]", "");
                                    if (!cleanNum.isEmpty() && !cleanNum.equals("****")) {
                                        oddsValue = Double.parseDouble(cleanNum);
                                    }
                                }
                            } catch (NumberFormatException e) {
                                oddsValue = 999.9;
                            }

                            horseList.add(new Horse(waku, umaban, name, jockeyName, weight, oddsValue));
                        }
                        horseList.sort(Comparator.comparingDouble(Horse::getOdds));
                        allRaces.add(new RaceInfo(i, venueName, raceName, raceTime, horseList));
//                        System.out.println(i + "R 取得完了");

                    } catch (Exception e) {
                        System.out.println(i + "Rの取得に失敗: " + e.getMessage());
//                        e.printStackTrace(); // 本番稼働時はコメントアウトしてもOKです
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("開催一覧の取得でエラーが発生しました: " + e.getMessage());
//            e.printStackTrace(); // 本番稼働時はコメントアウトしてもOKです
        }
        if (allRaces.isEmpty()) {
//            System.out.println("ダミーデータを使用します");
            System.out.println("実データが取得できませんでした");
            return createDummyRaces();
        }

        return allRaces;
    }
}
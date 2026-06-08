package org.example.keibaapp;

import org.springframework.stereotype.Service;
import java.util.List;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.Comparator;
import java.time.LocalTime;

@Service
public class RaceService {
    public List<RaceInfo> getRaces() {
        // ここに、これまで作った fetchAllRaces() の中身をコピペしてくる
        System.out.println("★ServiceのgetRacesが呼ばれました！"); // ←これを入れる
        return fetchAllRaces();
    }

    // ...ここに fetchAllRaces() や WebScraper クラスの中身を移植
    public List<RaceInfo> fetchAllRaces() {
        List<RaceInfo> allRaces = new ArrayList<>();
        System.out.println("★デバッグ: fetchAllRacesが起動しました");

        // --- 強制テストここから ---
        try {
            Document testDoc = WebScraper.getHTML("https://sports.yahoo.co.jp/keiba/");
            System.out.println("★デバッグ: タイトルは " + testDoc.title());
        } catch (Exception e) {
            System.out.println("★デバッグ: そもそもHTMLが取れていません！");
        }

        // 1. トップページから開催場リストを取得
        try{
            Document topDoc = WebScraper.getHTML("https://sports.yahoo.co.jp/keiba/");
            Elements raceListLinks = topDoc.select(".hr-raceProgress__link");

            for (Element link : raceListLinks) {
                String listUrl = link.attr("abs:href"); // 例: https://sports.yahoo.co.jp/keiba/race/list/26050302
                System.out.println("開催場を発見: " + listUrl);

                for (int i = 1; i <= 12; i++) {
                    try {
                        // ここでURLを生成
                        String raceUrl = listUrl.replace("/list/", "/denma/") + String.format("%02d", i);
                        String venueName = link.select(".hr-raceProgress__title").text();

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
                        System.out.println(i + "R 取得完了");

                    } catch (Exception e) {
                        System.out.println(i + "Rの取得に失敗: " + e.getMessage());
                        e.printStackTrace(); // 本番稼働時はコメントアウトしてもOKです
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("開催一覧の取得でエラーが発生しました: " + e.getMessage());
            e.printStackTrace(); // 本番稼働時はコメントアウトしてもOKです
        }
        return allRaces;
    }
}
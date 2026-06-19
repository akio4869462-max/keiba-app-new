package org.example.keibaapp;

import org.springframework.stereotype.Service;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.LocalTime;
import java.time.LocalDateTime;

@Service
public class RaceService {
    private List<RaceInfo> cachedRaces;
    private LocalDateTime lastFetchedAt;
    private final PredictionService predictionService;
    private final CsvExporter csvExporter;
    private final Map<String, HorseDetailInfo> horseDetailCache = new HashMap<>();

    public RaceService(PredictionService predictionService, CsvExporter csvExporter) {
        this.predictionService = predictionService;
        this.csvExporter = csvExporter;
    }

    public List<RaceInfo> fetchTodayRaces() {
        List<RaceInfo> allRaces = new ArrayList<>();
        System.out.println("★デバッグ: fetchTodayRacesが起動しました");

        // 1. トップページから開催場リストを取得
        try{
            String todayText = LocalDate.now()
                    .format(DateTimeFormatter.ofPattern("yyyy年M月d日"));
//            String todayText = ("2026年6月20日");

            Document topDoc = WebScraper.getHTML("https://sports.yahoo.co.jp/keiba/");
            System.out.println("トップページタイトル: " + topDoc.title());

            Elements raceListLinks = topDoc.select("a[href*=/keiba/race/list/]");
            System.out.println("開催場数 = " + raceListLinks.size());

            int venueCount = 0;

            for (Element link : raceListLinks) {

                String listUrl = link.attr("abs:href");

                Document listDoc = WebScraper.getHTML(listUrl);

                if (!listDoc.title().contains(todayText)) {
                    System.out.println("今日の開催ではないためスキップ: " + listDoc.title());
                    continue;
                }

                if (venueCount >= 3) {
                    break;
                }

                venueCount++;

                Elements raceLinks = listDoc.select("a[href*=/keiba/race/index/]");

                Set<String> raceUrls = new LinkedHashSet<>();


                for (Element raceLink : raceLinks) {
                    String indexUrl = raceLink.attr("abs:href");
                    String raceUrl = indexUrl.replace("/index/", "/denma/");
                    raceUrls.add(raceUrl);
                }

                String venueName = link.text();

                for (String raceUrl : raceUrls) {
                    try {
                        if (!raceUrl.endsWith("08")
                                && !raceUrl.endsWith("09")
                                && !raceUrl.endsWith("10")
                                && !raceUrl.endsWith("11")
                                && !raceUrl.endsWith("12")) {
                            continue;
                        }

                        Document doc = WebScraper.getHTML(raceUrl);

                        String raceName = WebScraper.getRaceName(doc);
                        String rawTime = WebScraper.getRaceTime(doc);
                        if (rawTime.length() == 4) {
                            rawTime = "0" + rawTime;
                        }
                        LocalTime raceTime = LocalTime.parse(rawTime);

                        List<Horse> horseList = new ArrayList<>();
                        Elements rows = getRaceRows(doc);

                        for (Element row : rows) {
                            if (row.selectFirst("th") != null || row.text().contains("枠番")) continue;
                            Elements tds = row.select("td");
                            if (tds.size() < 8) continue;

                            Horse horse = createHorse(tds);
                            enrichTodayHorse(horse);
                            horseList.add(horse);
                        }
                        sortHorsesByScore(horseList);
                        int raceNum = getRaceNumber(raceUrl);
                        allRaces.add(new RaceInfo(raceNum, venueName, raceName, raceTime, horseList));
//                        System.out.println(i + "R 取得完了");

                    } catch (Exception e) {
                        int raceNum = getRaceNumber(raceUrl);
                        System.out.println(raceNum + "Rの取得に失敗: " + e.getMessage());
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

        csvExporter.exportPredictions(allRaces);
        return allRaces;
    }

    private List<String> loadTargetListUrls() {
        try {
            return java.nio.file.Files.readAllLines(
                    java.nio.file.Paths.get("target_list_urls.txt")
            );
        } catch (Exception e) {
            System.out.println("URL一覧の読み込み失敗: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<RaceInfo> fetchHistoricalRaces() {
        List<RaceInfo> allRaces = new ArrayList<>();
        System.out.println("★デバッグ: fetchHistoricalRacesが起動しました");

        try {
            List<String> targetListUrls = loadTargetListUrls();

            for (String listUrl : targetListUrls) {

                Document listDoc = WebScraper.getHTML(listUrl);
                System.out.println("開催ページタイトル: " + listDoc.title());

                Elements raceLinks = listDoc.select("a[href*=/keiba/race/index/]");

                Set<String> raceUrls = new LinkedHashSet<>();

                for (Element raceLink : raceLinks) {
                    String indexUrl = raceLink.attr("abs:href");
                    String raceUrl = indexUrl.replace("/index/", "/denma/");
                    raceUrls.add(raceUrl);
                }

                String venueName = listDoc.title();

                for (String raceUrl : raceUrls) {
                    try {
                        if (!raceUrl.endsWith("07")
                                && !raceUrl.endsWith("08")
                                && !raceUrl.endsWith("09")
                                && !raceUrl.endsWith("10")
                                && !raceUrl.endsWith("11")
                                && !raceUrl.endsWith("12")) {
                            continue;
                        }

                        Document doc = WebScraper.getHTML(raceUrl);

                        String raceName = WebScraper.getRaceName(doc);

                        String rawTime = WebScraper.getRaceTime(doc);
                        if (rawTime.length() == 4) {
                            rawTime = "0" + rawTime;
                        }
                        LocalTime raceTime = LocalTime.parse(rawTime);

                        List<Horse> horseList = new ArrayList<>();
                        Elements rows = getRaceRows(doc);

                        for (Element row : rows) {
                            if (row.selectFirst("th") != null || row.text().contains("枠番")) {
                                continue;
                            }

                            Elements tds = row.select("td");
                            if (tds.size() < 8) {
                                continue;
                            }

                            Horse horse = createHorse(tds);
                            enrichHistoricalHorse(horse);
                            horseList.add(horse);
                        }

                        sortHorsesByScore(horseList);

                        int raceNum = getRaceNumber(raceUrl);
                        allRaces.add(new RaceInfo(raceNum, venueName, raceName, raceTime, horseList));

                    } catch (Exception e) {
                        int raceNum = getRaceNumber(raceUrl);
                        System.out.println(raceNum + "Rの取得に失敗: " + e.getMessage());
                    }
                }
            }

        } catch (Exception e) {
            System.out.println("開催一覧の取得でエラーが発生しました: " + e.getMessage());
        }

        if (allRaces.isEmpty()) {
            System.out.println("実データが取得できませんでした");
            return createDummyRaces();
        }

        csvExporter.exportPredictions(allRaces);
        return allRaces;
    }

    public List<RaceInfo> getRaces() {
        System.out.println("★ServiceのgetRacesが呼ばれました！");

        if (cachedRaces != null
                && lastFetchedAt != null
                && lastFetchedAt.plusMinutes(30).isAfter(LocalDateTime.now())) {

            System.out.println("キャッシュを使用します");
            return cachedRaces;
        }

        System.out.println("最新データを取得します");

        cachedRaces = fetchTodayRaces();
        lastFetchedAt = LocalDateTime.now();

        return cachedRaces;
    }


    private List<RaceInfo> createDummyRaces() {
        List<Horse> horses = new ArrayList<>();

        horses.add(new Horse("1", "1", "ダノンデサイル", "戸崎 圭太", "58.0", 7.3));
        horses.add(new Horse("1", "2", "ミュージアムマイル", "D.レーン", "54.0", 5.3));
        horses.add(new Horse("2", "3", "シュガークン", "吉村 誠之助", "58.0", 303.2));
        horses.add(new Horse("2", "4", "ミクニインスパイア", "丹内 祐次", "54.0", 65.3));
        horses.add(new Horse("3", "5", "クロワデュノール", "北村 友一", "54.0",1.8));
        horses.add(new Horse("3", "6", "ビザンチンドリーム", "西村 淳也", "58.0", 36.5));
        horses.add(new Horse("4", "7", "ファミリータイム", "幸 英明", "58.0", 332.8));
        horses.add(new Horse("4", "8", "タガノデュード", "高杉 吏麒", "58.0", 74.6));
        horses.add(new Horse("5", "9", "コスモキュランダ", "横山 武史", "58.0", 44.5));
        horses.add(new Horse("5", "10", "ジューンテイク", "松山 弘平", "58.0", 199.2));
        horses.add(new Horse("6", "11", "シンエンペラー", "坂井 瑠星", "58.0", 102.4));
        horses.add(new Horse("6", "12", "マイネルエンペラー", "川田 将雅", "58.0", 132.7));
        horses.add(new Horse("7", "13", "シェイクユアハート", "古川 吉洋", "58.0", 55.2));
        horses.add(new Horse("7", "14", "スティンガーグラス", "岩田 望来", "58.0", 125.0));
        horses.add(new Horse("7", "15", "マイユニバース", "横山 典弘", "54.0", 23.3));
        horses.add(new Horse("8", "16", "メイショウタバル", "武 豊", "58.0", 4.9));
        horses.add(new Horse("8", "17", "レガレイラ", "C.ルメール", "56.0", 10.5));
        horses.add(new Horse("8", "18", "ミステリーウェイ", "松本 大輝", "58.0", 249.3));

        List<RaceInfo> races = new ArrayList<>();

        races.add(new RaceInfo(
                11,
                "3回阪神4日",
                "宝塚記念 GI",
                LocalTime.of(15, 40),
                horses
        ));

        PastRaceInfo[] lastRaces = {
                new PastRaceInfo("ダミーレース",3, "GI", 4),
                new PastRaceInfo("ダミーレース",1, "GIII", 2),
                new PastRaceInfo("ダミーレース",15, "条件戦", 8),
                new PastRaceInfo("ダミーレース",2, "条件戦", 3),
                new PastRaceInfo("ダミーレース",1, "GI", 1),
                new PastRaceInfo("ダミーレース",2, "GII", 5),
                new PastRaceInfo("ダミーレース",9, "条件戦", 10),
                new PastRaceInfo("ダミーレース",6, "条件戦", 7),
                new PastRaceInfo("ダミーレース",7, "GIII", 6),
                new PastRaceInfo("ダミーレース",4, "条件戦", 4),
                new PastRaceInfo("ダミーレース",7, "GI", 3),
                new PastRaceInfo("ダミーレース",5, "GII", 5),
                new PastRaceInfo("ダミーレース",1, "OP", 2),
                new PastRaceInfo("ダミーレース",1, "L", 1),
                new PastRaceInfo("ダミーレース",1, "条件戦", 1),
                new PastRaceInfo("ダミーレース",2, "GIII", 1),
                new PastRaceInfo("ダミーレース",4, "GI", 2),
                new PastRaceInfo("ダミーレース",8, "条件戦", 9)
        };

        PastRaceInfo[] secondLastRaces = {
                new PastRaceInfo("ダミーレース",5, "GII", 3),
                new PastRaceInfo("ダミーレース",2, "GI", 5),
                new PastRaceInfo("ダミーレース",8, "条件戦", 6),
                new PastRaceInfo("ダミーレース",1, "条件戦", 2),
                new PastRaceInfo("ダミーレース",2, "GI", 1),
                new PastRaceInfo("ダミーレース",6, "GIII", 4),
                new PastRaceInfo("ダミーレース",12, "条件戦", 12),
                new PastRaceInfo("ダミーレース",4, "条件戦", 8),
                new PastRaceInfo("ダミーレース",3, "GIII", 5),
                new PastRaceInfo("ダミーレース",7, "条件戦", 9),
                new PastRaceInfo("ダミーレース",3, "GII", 2),
                new PastRaceInfo("ダミーレース",6, "GIII", 6),
                new PastRaceInfo("ダミーレース",4, "OP", 3),
                new PastRaceInfo("ダミーレース",5, "L", 4),
                new PastRaceInfo("ダミーレース",2, "条件戦", 2),
                new PastRaceInfo("ダミーレース",1, "GII", 1),
                new PastRaceInfo("ダミーレース",1, "GI", 3),
                new PastRaceInfo("ダミーレース",10, "条件戦", 11)
        };

        PastRaceInfo[] thirdLastRaces = {
                new PastRaceInfo("ダミーレース",2, "GIII", 2),
                new PastRaceInfo("ダミーレース",1, "GII", 4),
                new PastRaceInfo("ダミーレース",10, "条件戦", 10),
                new PastRaceInfo("ダミーレース",4, "条件戦", 5),
                new PastRaceInfo("ダミーレース",1, "GIII", 1),
                new PastRaceInfo("ダミーレース",3, "GII", 3),
                new PastRaceInfo("ダミーレース",8, "条件戦", 9),
                new PastRaceInfo("ダミーレース",5, "条件戦", 7),
                new PastRaceInfo("ダミーレース",6, "GI", 8),
                new PastRaceInfo("ダミーレース",5, "条件戦", 6),
                new PastRaceInfo("ダミーレース",2, "GI", 2),
                new PastRaceInfo("ダミーレース",4, "GII", 4),
                new PastRaceInfo("ダミーレース",6, "OP", 5),
                new PastRaceInfo("ダミーレース",2, "L", 2),
                new PastRaceInfo("ダミーレース",3, "条件戦", 3),
                new PastRaceInfo("ダミーレース",5, "GI", 6),
                new PastRaceInfo("ダミーレース",2, "GII", 2),
                new PastRaceInfo("ダミーレース",12, "条件戦", 12)
        };

        for (int i = 0; i < horses.size(); i++) {
            horses.get(i).setLastRace(lastRaces[i]);
            horses.get(i).setSecondLastRace(secondLastRaces[i]);
            horses.get(i).setThirdLastRace(thirdLastRaces[i]);
        }

        for (Horse hors : horses) {
            hors.setPredictionScore(
                    predictionService.calculateScore(hors)
            );
            hors.setPredictionReason(
                    predictionService.createReason(hors)
            );
        }

        horses.sort(
                Comparator.comparingDouble(Horse::getPredictionScore)
                        .reversed()
        );

        return races;
    }

    private double parseOdds(String rawOdds) {

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

                String cleanNum =
                        rawOdds.replaceAll("[^0-9.]", "");

                if (!cleanNum.isEmpty()
                        && !cleanNum.equals("****")) {

                    oddsValue =
                            Double.parseDouble(cleanNum);
                }
            }

        } catch (NumberFormatException e) {
            oddsValue = 999.9;
        }

        return oddsValue;
    }

    private Horse createHorse(Elements tds) {

        String waku = tds.get(0).text().trim();
        String umaban = tds.get(1).text().trim();

        Element horseLink = tds.get(2).selectFirst("a");

        String name = tds.get(2).text().split("\\s+")[0];
        String horseUrl = horseLink != null
                ? horseLink.attr("abs:href")
                : "";

        String jockeyData = tds.get(3).text().trim();

        String jockeyName =
                jockeyData.substring(
                        0,
                        jockeyData.lastIndexOf(' ')
                ).trim();

        String weight =
                jockeyData.substring(
                        jockeyData.lastIndexOf(' ') + 1
                );

        double oddsValue =
                parseOdds(tds.get(7).text().trim());

        Horse horse = new Horse(
                waku,
                umaban,
                name,
                jockeyName,
                weight,
                oddsValue
        );

        horse.setHorseUrl(horseUrl);

        return horse;
    }

    private void enrichTodayHorse(Horse horse) throws InterruptedException {
        String horseUrl = horse.getHorseUrl();
        String cacheKey = "today:" + horseUrl;

        HorseDetailInfo detail = horseDetailCache.get(cacheKey);

        if (detail == null) {
            Thread.sleep(500);
            detail = WebScraper.getTodayHorseDetailInfo(horseUrl);
            horseDetailCache.put(cacheKey, detail);
        }

        horse.setLastRace(detail.getLastRace());
        horse.setSecondLastRace(detail.getSecondLastRace());
        horse.setThirdLastRace(detail.getThirdLastRace());

        horse.setPredictionScore(predictionService.calculateScore(horse));
        horse.setPredictionReason(predictionService.createReason(horse));
    }

    private void enrichHistoricalHorse(Horse horse) throws InterruptedException {
        String horseUrl = horse.getHorseUrl();
        String cacheKey = "historical:" + horseUrl;

        HorseDetailInfo detail = horseDetailCache.get(cacheKey);

        if (detail == null) {
            Thread.sleep(500);
            detail = WebScraper.getHistoricalHorseDetailInfo(horseUrl);
            horseDetailCache.put(cacheKey, detail);
        }

        horse.setLastRace(detail.getLastRace());
        horse.setSecondLastRace(detail.getSecondLastRace());
        horse.setThirdLastRace(detail.getThirdLastRace());
        horse.setActualRace(detail.getActualRace());

        horse.setPredictionScore(predictionService.calculateScore(horse));
        horse.setPredictionReason(predictionService.createReason(horse));
    }

    private int getRaceNumber(String raceUrl) {
        String cleanUrl = raceUrl.endsWith("/")
                ? raceUrl.substring(0, raceUrl.length() - 1)
                : raceUrl;

        String raceNoText = cleanUrl.substring(cleanUrl.length() - 2);
        return Integer.parseInt(raceNoText);
    }

    private void sortHorsesByScore(List<Horse> horseList) {
        horseList.sort(
                Comparator.comparingDouble(Horse::getPredictionScore)
                        .reversed()
        );
    }

    private Elements getRaceRows(Document doc) {
        Elements rows = doc.select(".hr-table tbody tr");

        if (rows.isEmpty()) {
            return doc.select("table tr");
        }

        return rows;
    }
}
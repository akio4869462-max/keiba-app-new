package org.example.keibaapp;

import org.springframework.stereotype.Service;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.time.LocalTime;
import java.time.LocalDateTime;

@Service
public class RaceService {
    private List<RaceInfo> cachedRaces;
    private LocalDateTime lastFetchedAt;
    private String cachedRange;
    private final PredictionService predictionService;
    private final CsvExporter csvExporter;
    private final Map<String, HorseDetailInfo> horseDetailCache = new HashMap<>();
    private final DummyRaceFactory dummyRaceFactory;

    public RaceService(
            PredictionService predictionService,
            CsvExporter csvExporter,
            DummyRaceFactory dummyRaceFactory) {

        this.predictionService = predictionService;
        this.csvExporter = csvExporter;
        this.dummyRaceFactory = dummyRaceFactory;
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

                Set<String> raceUrls = getRaceUrls(listDoc);

                String venueName = link.text();

                for (String raceUrl : raceUrls) {
                    try {
                        int[] range = getRaceRangeByTime();

                        if (!shouldFetchRace(raceUrl, range[0], range[1])) {
                            continue;
                        }

                        Document doc = WebScraper.getHTML(raceUrl);

                        String raceName = WebScraper.getRaceName(doc);

                        LocalTime raceTime = parseRaceTime(doc);

                        List<Horse> horseList = createTodayHorseList(doc);

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
            return dummyRaceFactory.createDummyRaces();
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

                Set<String> raceUrls = getRaceUrls(listDoc);

                String venueName = listDoc.title();

                for (String raceUrl : raceUrls) {
                    try {
                        if (!shouldFetchRace(raceUrl, 7, 12)) {
                            continue;
                        }

                        Document doc = WebScraper.getHTML(raceUrl);

                        String raceName = WebScraper.getRaceName(doc);

                        LocalTime raceTime = parseRaceTime(doc);

                        List<Horse> horseList = createHistoricalHorseList(doc);

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
            return dummyRaceFactory.createDummyRaces();
        }

        csvExporter.exportPredictions(allRaces);
        return allRaces;
    }

    public List<RaceInfo> getRaces() {

        System.out.println("★ServiceのgetRacesが呼ばれました！");

        int[] range = getRaceRangeByTime();
        String currentRange = range[0] + "-" + range[1];

        if (cachedRaces != null
                && currentRange.equals(cachedRange)
                && lastFetchedAt != null
                && lastFetchedAt.plusMinutes(30).isAfter(LocalDateTime.now())) {

            System.out.println("キャッシュを使用します");
            return cachedRaces;
        }

        System.out.println("最新データを取得します");

        cachedRaces = fetchTodayRaces();
        cachedRange = currentRange;
        lastFetchedAt = LocalDateTime.now();

        return cachedRaces;
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
                OddsParser.parse(tds.get(7).text().trim());

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
        HorseDetailInfo detail = getHorseDetail(horse.getHorseUrl(), false);

        horse.setLastRace(detail.getLastRace());
        horse.setSecondLastRace(detail.getSecondLastRace());
        horse.setThirdLastRace(detail.getThirdLastRace());

        horse.setPredictionScore(predictionService.calculateScore(horse));
        horse.setPredictionReason(predictionService.createReason(horse));
    }

    private void enrichHistoricalHorse(Horse horse) throws InterruptedException {
        HorseDetailInfo detail = getHorseDetail(horse.getHorseUrl(), true);

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

    private boolean shouldFetchRace(String raceUrl, int startRace, int endRace) {
        int raceNum = getRaceNumber(raceUrl);
        return raceNum >= startRace && raceNum <= endRace;
    }

    private int[] getRaceRangeByTime() {
        LocalTime now = LocalTime.now();

        if (now.isBefore(LocalTime.of(11, 30))) {
            return new int[]{1, 4};
        } else if (now.isBefore(LocalTime.of(14, 0))) {
            return new int[]{5, 8};
        } else {
            return new int[]{9, 12};
        }
    }

    private Set<String> getRaceUrls(Document listDoc) {
        Elements raceLinks = listDoc.select("a[href*=/keiba/race/index/]");

        Set<String> raceUrls = new LinkedHashSet<>();

        for (Element raceLink : raceLinks) {
            String indexUrl = raceLink.attr("abs:href");
            String raceUrl = indexUrl.replace("/index/", "/denma/");
            raceUrls.add(raceUrl);
        }

        return raceUrls;
    }

    private List<Horse> createTodayHorseList(Document doc) throws InterruptedException {
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
            enrichTodayHorse(horse);
            horseList.add(horse);
        }

        sortHorsesByScore(horseList);
        return horseList;
    }

    private List<Horse> createHistoricalHorseList(Document doc) throws InterruptedException {
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
        return horseList;
    }

    private HorseDetailInfo getHorseDetail(String horseUrl, boolean historical) throws InterruptedException {
        String cacheKey = historical
                ? "historical:" + horseUrl
                : "today:" + horseUrl;

        HorseDetailInfo detail = horseDetailCache.get(cacheKey);

        if (detail == null) {
            Thread.sleep(500);

            detail = historical
                    ? WebScraper.getHistoricalHorseDetailInfo(horseUrl)
                    : WebScraper.getTodayHorseDetailInfo(horseUrl);

            horseDetailCache.put(cacheKey, detail);
        }

        return detail;
    }

    private LocalTime parseRaceTime(Document doc) {
        String rawTime = WebScraper.getRaceTime(doc);

        if (rawTime.length() == 4) {
            rawTime = "0" + rawTime;
        }

        return LocalTime.parse(rawTime);
    }
}
package org.example.keibaapp;

import org.springframework.stereotype.Service;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.time.LocalTime;

@Service
public class RaceService {
    private final CsvExporter csvExporter;
    private final DummyRaceFactory dummyRaceFactory;
    private final RaceCacheService raceCacheService;
    private final HorseEnrichmentService horseEnrichmentService;
    private final RaceParserService raceParserService;

    public RaceService(
            CsvExporter csvExporter,
            DummyRaceFactory dummyRaceFactory,
            RaceCacheService raceCacheService,
            HorseEnrichmentService horseEnrichmentService,
            RaceParserService raceParserService) {

        this.csvExporter = csvExporter;
        this.dummyRaceFactory = dummyRaceFactory;
        this.raceCacheService = raceCacheService;
        this.horseEnrichmentService = horseEnrichmentService;
        this.raceParserService = raceParserService;
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

                Set<String> raceUrls = raceParserService.getRaceUrls(listDoc);

                String venueName =
                        raceParserService.extractVenueName(listDoc.title());

                for (String raceUrl : raceUrls) {
                    try {
                        int[] range = raceParserService.getRaceRangeByTime();

                        if (!raceParserService.shouldFetchRace(raceUrl, range[0], range[1])) {
                            continue;
                        }

                        Document doc = WebScraper.getHTML(raceUrl);

                        String raceName = WebScraper.getRaceName(doc);

                        LocalTime raceTime = raceParserService.parseRaceTime(doc);

                        int raceNum = raceParserService.getRaceNumber(raceUrl);

                        String course = WebScraper.getRaceCourse(doc);

                        String distance = WebScraper.getRaceDistance(doc);

                        System.out.println("今回距離=" + distance);

                        List<Horse> horseList = createTodayHorseList(doc, course, distance);

                        RaceInfo raceInfo = new RaceInfo(
                                raceNum,
                                venueName,
                                raceName,
                                raceTime,
                                course,
                                distance,
                                horseList
                        );

                        horseEnrichmentService.enrichAiPrompt(raceInfo);

                        allRaces.add(raceInfo);
//                        System.out.println(i + "R 取得完了");

                    } catch (Exception e) {
                        int raceNum = raceParserService.getRaceNumber(raceUrl);
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

                Set<String> raceUrls = raceParserService.getRaceUrls(listDoc);

                String venueName =
                        raceParserService.extractVenueName(listDoc.title());

                for (String raceUrl : raceUrls) {
                    try {
                        if (!raceParserService.shouldFetchRace(raceUrl, 9, 11)) {
                            continue;
                        }

                        Document doc = WebScraper.getHTML(raceUrl);

                        String raceName = WebScraper.getRaceName(doc);

                        LocalTime raceTime = raceParserService.parseRaceTime(doc);

                        int raceNum = raceParserService.getRaceNumber(raceUrl);

                        String course = WebScraper.getRaceCourse(doc);

                        String distance = WebScraper.getRaceDistance(doc);

                        List<Horse> horseList = createHistoricalHorseList(doc, course,distance);

                        RaceInfo raceInfo = new RaceInfo(
                                raceNum,
                                venueName,
                                raceName,
                                raceTime,
                                course,
                                distance,
                                horseList
                        );

                        horseEnrichmentService.enrichAiPrompt(raceInfo);

                        allRaces.add(raceInfo);

                    } catch (Exception e) {
                        int raceNum = raceParserService.getRaceNumber(raceUrl);
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

        int[] range = raceParserService.getRaceRangeByTime();
        String currentRange = range[0] + "-" + range[1];

        if (raceCacheService.isRaceCacheValid(currentRange)) {
            System.out.println("キャッシュを使用します");
            return raceCacheService.getCachedRaces();
        }

        System.out.println("最新データを取得します");

        List<RaceInfo> races = fetchTodayRaces();
//        List<RaceInfo> races = fetchHistoricalRaces();

        raceCacheService.cacheRaces(currentRange, races);

        return races;
    }

    private void sortHorsesByScore(List<Horse> horseList) {
        horseList.sort(
                Comparator.comparingDouble(Horse::getPredictionScore)
                        .reversed()
        );
    }

    private List<Horse> createTodayHorseList(
            Document doc,
            String currentCourse,
            String currentDistance) throws InterruptedException {
        List<Horse> horseList = new ArrayList<>();
        Elements rows = raceParserService.getRaceRows(doc);

        for (Element row : rows) {
            if (row.selectFirst("th") != null || row.text().contains("枠番")) {
                continue;
            }

            Elements tds = row.select("td");
            if (tds.size() < 8) {
                continue;
            }

            Horse horse = raceParserService.createHorse(tds);
            horseEnrichmentService.enrichTodayHorse(horse, currentCourse, currentDistance);
            horseList.add(horse);
        }

        sortHorsesByScore(horseList);
        return horseList;
    }

    private List<Horse> createHistoricalHorseList(
            Document doc,
            String currentCourse,
            String currentDistance) throws InterruptedException {
        List<Horse> horseList = new ArrayList<>();
        Elements rows = raceParserService.getRaceRows(doc);

        for (Element row : rows) {
            if (row.selectFirst("th") != null || row.text().contains("枠番")) {
                continue;
            }

            Elements tds = row.select("td");
            if (tds.size() < 8) {
                continue;
            }

            Horse horse = raceParserService.createHorse(tds);
            horseEnrichmentService.enrichHistoricalHorse(horse, currentCourse, currentDistance);
            horseList.add(horse);
        }

        sortHorsesByScore(horseList);
        return horseList;
    }
}
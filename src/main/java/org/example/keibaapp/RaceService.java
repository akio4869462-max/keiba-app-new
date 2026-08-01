package org.example.keibaapp;

import org.springframework.stereotype.Service;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.time.LocalTime;

@Service
public class RaceService {
    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    private final CsvExporter csvExporter;
    private final DummyRaceFactory dummyRaceFactory;
    private final RaceCacheService raceCacheService;
    private final HorseEnrichmentService horseEnrichmentService;
    private final RaceParserService raceParserService;
    private final TrackedRaceUrlRepository trackedRaceUrlRepository;

    public RaceService(
            CsvExporter csvExporter,
            DummyRaceFactory dummyRaceFactory,
            RaceCacheService raceCacheService,
            HorseEnrichmentService horseEnrichmentService,
            RaceParserService raceParserService,
            TrackedRaceUrlRepository trackedRaceUrlRepository) {

        this.csvExporter = csvExporter;
        this.dummyRaceFactory = dummyRaceFactory;
        this.raceCacheService = raceCacheService;
        this.horseEnrichmentService = horseEnrichmentService;
        this.raceParserService = raceParserService;
        this.trackedRaceUrlRepository = trackedRaceUrlRepository;
    }

    private void trackRaceUrls(Set<String> raceUrls) {
        LocalDate today = LocalDate.now(JST);

        for (String raceUrl : raceUrls) {
            if (trackedRaceUrlRepository.findByRaceUrl(raceUrl).isEmpty()) {
                trackedRaceUrlRepository.save(new TrackedRaceUrl(raceUrl, today));
            }
        }
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

                trackRaceUrls(raceUrls);

                String venueName =
                        raceParserService.extractVenueName(listDoc.title());

                for (String raceUrl : raceUrls) {
                    try {
                        // 壁時計からレース番号を推測するのではなく、denmaページを取得して
                        // 実際の発走時刻を見てから「今表示する価値があるか」を判定する
                        // (夏の変則開催等で発走時刻が通常と大きくずれても正しく動くようにするため)
                        Document doc = WebScraper.getHTML(raceUrl);

                        LocalTime raceTime = raceParserService.parseRaceTime(doc);

                        if (!raceParserService.isRaceTimeRelevant(raceTime)) {
                            continue;
                        }

                        String raceName = WebScraper.getRaceName(doc);

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

    // 今日JRAの開催があるかどうかだけを軽量に判定する。レース単位の詳細取得は行わない。
    // 曜日をハードコードせずスケジュール処理を毎日実行できるようにするために使う
    public boolean hasRaceToday() {
        try {
            String todayText = LocalDate.now()
                    .format(DateTimeFormatter.ofPattern("yyyy年M月d日"));

            Document topDoc = WebScraper.getHTML("https://sports.yahoo.co.jp/keiba/");

            for (Element link : topDoc.select("a[href*=/keiba/race/list/]")) {
                Document listDoc = WebScraper.getHTML(link.attr("abs:href"));

                if (listDoc.title().contains(todayText)) {
                    return true;
                }
            }
        } catch (Exception e) {
            System.out.println("開催日判定でエラーが発生しました: " + e.getMessage());
        }

        return false;
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

        // レース番号の範囲ではなく、現在時刻を30分単位に丸めた値をキャッシュキーにする。
        // (発走時刻ベースの関連レース判定に変えたため、レース番号の範囲では
        // キャッシュの区切りを表現できなくなったため)
        LocalTime now = LocalTime.now(JST);
        String currentRange = now.getHour() + ":" + (now.getMinute() / 30 * 30);

        // DEBUG
        System.out.println("キャッシュキー=" + currentRange);

        if (raceCacheService.isRaceCacheValid(currentRange)) {
            System.out.println("キャッシュを使用します");
            return raceCacheService.getCachedRaces();
        }

        System.out.println("最新データを取得します");

        // 曜日をハードコードせず毎日この経路が呼ばれうるため、今日開催がなければ
        // ダミーを返すだけにし、お気に入り通知チェックに使われるキャッシュには
        // 書き込まない(非開催日にダミーの馬名でお気に入りが誤反応しないようにするため)
        if (!hasRaceToday()) {
            System.out.println("本日は開催がないためダミーデータを返します");
            return dummyRaceFactory.createDummyRaces();
        }

        List<RaceInfo> races = fetchTodayRaces();
//        List<RaceInfo> races = fetchHistoricalRaces();

        raceCacheService.cacheRaces(currentRange, races);

        return races;
    }

    public List<RaceInfo> getBasicRaces() {
        List<RaceInfo> allRaces = new ArrayList<>();

        try {
            String todayText = LocalDate.now()
                    .format(DateTimeFormatter.ofPattern("yyyy年M月d日"));

            Document topDoc = WebScraper.getHTML("https://sports.yahoo.co.jp/keiba/");

            Elements raceListLinks = topDoc.select("a[href*=/keiba/race/list/]");

            int venueCount = 0;

            for (Element link : raceListLinks) {
                String listUrl = link.attr("abs:href");
                Document listDoc = WebScraper.getHTML(listUrl);

                if (!listDoc.title().contains(todayText)) continue;
                if (venueCount >= 3) break;
                venueCount++;

                Set<String> raceUrls = raceParserService.getRaceUrls(listDoc);
                String venueName = raceParserService.extractVenueName(listDoc.title());

                for (String raceUrl : raceUrls) {
                    try {
                        Document doc = WebScraper.getHTML(raceUrl);

                        LocalTime raceTime = raceParserService.parseRaceTime(doc);

                        if (!raceParserService.isRaceTimeRelevant(raceTime)) continue;

                        RaceInfo raceInfo = new RaceInfo(
                                raceParserService.getRaceNumber(raceUrl),
                                venueName,
                                WebScraper.getRaceName(doc),
                                raceTime,
                                WebScraper.getRaceCourse(doc),
                                WebScraper.getRaceDistance(doc),
                                buildBasicHorseList(doc)
                        );

                        allRaces.add(raceInfo);

                    } catch (Exception e) {
                        System.out.println(raceParserService.getRaceNumber(raceUrl) + "R取得失敗: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("出馬表取得エラー: " + e.getMessage());
        }

        if (allRaces.isEmpty()) {
            return dummyRaceFactory.createDummyRaces();
        }

        return allRaces;
    }

    private List<Horse> buildBasicHorseList(Document doc) {
        List<Horse> horseList = new ArrayList<>();
        Elements rows = raceParserService.getRaceRows(doc);

        for (Element row : rows) {
            if (row.selectFirst("th") != null || row.text().contains("枠番")) continue;
            Elements tds = row.select("td");
            if (tds.size() < 8) continue;
            horseList.add(raceParserService.createHorse(tds));
        }

        return horseList;
    }

    private void sortHorsesByScore(List<Horse> horseList) {
        horseList.sort(
                Comparator.comparingDouble(Horse::getPredictionScore)
                        .reversed()
        );
    }

    public List<Horse> buildHorseList(Document doc,
                                    String currentCourse,
                                    String currentDistance,
                                    boolean isHistorical) throws InterruptedException {
        List<Horse> horseList = new ArrayList<>();
        Elements rows = raceParserService.getRaceRows(doc);

        for (Element row : rows) {
            if (row.selectFirst("th") != null || row.text().contains("枠番")) continue;
            Elements tds = row.select("td");
            if (tds.size() < 8) continue;

            Horse horse = raceParserService.createHorse(tds);

            horseEnrichmentService.fetchHorseDetail(horse, isHistorical);
            horseEnrichmentService.enrichJockeyStats(horse);

            horseList.add(horse);
        }

        for (Horse horse : horseList) {
            horseEnrichmentService.applyScore(horse, horseList, currentCourse, currentDistance);
        }

        sortHorsesByScore(horseList);
        return horseList;
    }

    private List<Horse> createTodayHorseList(
            Document doc,
            String currentCourse,
            String currentDistance) throws InterruptedException {
        return buildHorseList(doc, currentCourse, currentDistance, false);
    }

    private List<Horse> createHistoricalHorseList(
            Document doc,
            String currentCourse,
            String currentDistance) throws InterruptedException {
        return buildHorseList(doc, currentCourse, currentDistance, true);
    }
}
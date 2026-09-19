package org.example.keibaapp;

import org.springframework.scheduling.annotation.Scheduled;
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

                // 開催場一覧ページには各レースの発走時刻が既に載っているため、
                // denmaページを開く前にここで関連レースを絞り込む
                // (以前は全レースのdenmaページを取得してから時刻判定していたため、
                // 無関係なレースの分まで毎回取得してしまい表示が遅くなっていた)
                for (RaceParserService.RaceSchedule schedule : raceParserService.getRaceSchedules(listDoc)) {
                    if (!raceParserService.isRaceTimeRelevant(schedule.raceTime())) {
                        continue;
                    }

                    String raceUrl = schedule.raceUrl();

                    try {
                        Document doc = WebScraper.getHTML(raceUrl);

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
                                schedule.raceTime(),
                                course,
                                distance,
                                horseList
                        );
                        raceInfo.setRaceUrl(raceUrl);

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

    // 予想モデルの妙味(overlay)計算に使う重みは締切10分前オッズで較正されている
    // (MODEL_REVISION.md §2.2)。毎時の全体リフレッシュ(getRaces())だけでは
    // 最大1時間近く古いオッズのままになるため、発走10分前になったレースだけ
    // そのレース1件分のdenmaページを再取得してオッズと予想モデルを更新する。
    // 1分おきに実行するが、キャッシュが無ければ即終了し、対象レースが窓に入った
    // 最初の1回しかネットワークアクセスしない(raceCacheServiceのガード)ため、
    // 毎時10レース前後を取り直す全体リフレッシュよりアクセス負荷ははるかに小さい
    @Scheduled(cron = "0 * * * * *", zone = "Asia/Tokyo")
    public void refreshOddsNearPost() {
        List<RaceInfo> races = raceCacheService.getCachedRaces();

        if (races == null || races.isEmpty()) {
            return;
        }

        for (RaceInfo race : races) {
            String raceUrl = race.getRaceUrl();

            if (raceUrl == null) {
                continue;
            }

            if (!raceParserService.isWithinFinalOddsRefreshWindow(race.getRaceTime())) {
                continue;
            }

            if (raceCacheService.wasOddsRefreshed(raceUrl)) {
                continue;
            }

            try {
                Document doc = WebScraper.getHTML(raceUrl);
                List<Horse> horseList = createTodayHorseList(doc, race.getCourse(), race.getDistance());
                race.setHorses(horseList);
                System.out.println("【締切前オッズ再取得】完了: " + race.getDisplayRaceName());
            } catch (Exception e) {
                System.out.println("【締切前オッズ再取得】失敗: " + race.getDisplayRaceName() + " / " + e.getMessage());
            } finally {
                // 失敗時も毎分リトライして無駄なアクセスを繰り返さないよう、
                // 成功可否によらず1回試行したら完了扱いにする
                raceCacheService.markOddsRefreshed(raceUrl);
            }
        }
    }

    // /races(出馬表)専用のキャッシュ。getRaces()と同じ90分TTL・30分単位の
    // キャッシュキーの仕組みを流用する
    public List<RaceInfo> getBasicRaces() {
        LocalTime now = LocalTime.now(JST);
        String currentRange = now.getHour() + ":" + (now.getMinute() / 30 * 30);

        if (raceCacheService.isBasicRaceCacheValid(currentRange)) {
            return raceCacheService.getCachedBasicRaces();
        }

        if (!hasRaceToday()) {
            return dummyRaceFactory.createDummyRaces();
        }

        List<RaceInfo> races = fetchBasicRaces();

        raceCacheService.cacheBasicRaces(currentRange, races);

        return races;
    }

    private List<RaceInfo> fetchBasicRaces() {
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

                String venueName = raceParserService.extractVenueName(listDoc.title());

                // 開催場一覧ページの発走時刻で先に関連レースを絞り込んでからdenmaページを取得する。
                // 出馬表は終わったレースもその日のうちは見られるようにしたいので、
                // 過去方向には制限を設けない判定を使う
                for (RaceParserService.RaceSchedule schedule : raceParserService.getRaceSchedules(listDoc)) {
                    if (!raceParserService.isRaceStillShowableToday(schedule.raceTime())) continue;

                    String raceUrl = schedule.raceUrl();
                    int raceNumber = raceParserService.getRaceNumber(raceUrl);

                    // 発走時刻を過ぎたレースの出走内容はもう変わらないため、
                    // レース単位のキャッシュがあればdenmaページを取得し直さない。
                    // (リスト単位キャッシュのTTLが切れるたびに、既に終わったレースまで
                    // 毎回再取得してしまい、時間が経つほどコストが増えるのを防ぐ)
                    boolean isFinished = LocalTime.now(JST).isAfter(schedule.raceTime());

                    if (isFinished) {
                        RaceInfo cached = raceCacheService.getFinishedRace(venueName, raceNumber);
                        if (cached != null) {
                            allRaces.add(cached);
                            continue;
                        }
                    }

                    try {
                        Document doc = WebScraper.getHTML(raceUrl);

                        RaceInfo raceInfo = new RaceInfo(
                                raceNumber,
                                venueName,
                                WebScraper.getRaceName(doc),
                                schedule.raceTime(),
                                WebScraper.getRaceCourse(doc),
                                WebScraper.getRaceDistance(doc),
                                buildBasicHorseList(doc)
                        );

                        allRaces.add(raceInfo);

                        if (isFinished) {
                            raceCacheService.cacheFinishedRace(venueName, raceNumber, raceInfo);
                        }

                    } catch (Exception e) {
                        System.out.println(raceNumber + "R取得失敗: " + e.getMessage());
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

    // currentCourse/currentDistanceは現在の予想モデル(市場確率ベースのみ)では
    // 未使用だが、コース・距離別の特徴量を追加する将来フェーズ(MODEL_REVISION.md §6)
    // のためにシグネチャを維持している
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

        horseEnrichmentService.applyRaceModel(horseList);

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
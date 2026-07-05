package org.example.keibaapp;

import org.jsoup.nodes.Document;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class RaceResultCollectionService {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    // 発走から確定を試みるまでの猶予（レース自体は数分で終わるが、着順確定・
    // 結果ページへの反映にはさらに時間がかかるため余裕を持たせる）
    private static final int RESULT_CONFIRMATION_BUFFER_MINUTES = 40;

    private final TrackedRaceUrlRepository trackedRaceUrlRepository;
    private final RaceResultRecordRepository raceResultRecordRepository;
    private final RaceService raceService;
    private final RaceParserService raceParserService;

    public RaceResultCollectionService(
            TrackedRaceUrlRepository trackedRaceUrlRepository,
            RaceResultRecordRepository raceResultRecordRepository,
            RaceService raceService,
            RaceParserService raceParserService) {

        this.trackedRaceUrlRepository = trackedRaceUrlRepository;
        this.raceResultRecordRepository = raceResultRecordRepository;
        this.raceService = raceService;
        this.raceParserService = raceParserService;
    }

    // 手動実行(/results/collect)用。1頭ごとに0.5秒待ちながら順に取得するため
    // レース数が多いと数十秒〜数分かかる。ブラウザを待たせないよう非同期で実行し、
    // 結果は/results/racesや/results/debugで確認してもらう
    @Async
    public void collectWeekendResultsAsync() {
        collectWeekendResults();
    }

    // 土日の日中は30分おきに確定済みレースがないか確認し、順次結果を反映する
    // （未確定のレースはprocessRaceUrl内でスキップされ次回に再試行されるだけなので安全）
    @Scheduled(cron = "0 */30 9-18 * * SAT,SUN", zone = "Asia/Tokyo")
    // 土日に取りこぼした分の最終catch-allとして月曜早朝にも実行する
    @Scheduled(cron = "0 0 3 * * MON", zone = "Asia/Tokyo")
    public String collectWeekendResults() {
        List<TrackedRaceUrl> pending = trackedRaceUrlRepository.findByProcessedFalse();

        int confirmed = 0;
        int pendingResult = 0;
        int failed = 0;

        for (TrackedRaceUrl tracked : pending) {
            try {
                if (processRaceUrl(tracked)) {
                    confirmed++;
                } else {
                    pendingResult++;
                }
            } catch (Exception e) {
                failed++;
                System.out.println("結果収集失敗: " + tracked.getRaceUrl() + " / " + e.getMessage());
            }
        }

        String summary = "対象" + pending.size() + "件 / 確定" + confirmed
                + "件 / 結果未確定" + pendingResult + "件 / 失敗" + failed + "件";

        System.out.println("【結果収集】" + summary);

        return summary;
    }

    private boolean processRaceUrl(TrackedRaceUrl tracked) throws InterruptedException, java.io.IOException {
        String raceUrl = tracked.getRaceUrl();

        Document doc = WebScraper.getHTML(raceUrl);

        // サイト側は発走前でも「結果」欄に古いデータ(前走時点のキャッシュ等)を
        // 表示していることがあり、内容だけでは確定判定を信用できない。
        // そのため発走時刻から十分な時間が経っているかを最初にチェックする。
        // LocalTimeは日付を持たないため、tracked.getRaceDate()（追跡開始時の
        // 実際の日付）と組み合わせて絶対時刻同士で比較する
        LocalTime raceTime = raceParserService.parseRaceTime(doc);
        LocalDateTime raceDateTime = LocalDateTime.of(tracked.getRaceDate(), raceTime);
        LocalDateTime now = LocalDateTime.now(JST);

        if (now.isBefore(raceDateTime.plusMinutes(RESULT_CONFIRMATION_BUFFER_MINUTES))) {
            System.out.println(
                    "発走(" + raceDateTime + ")から間もないためスキップ(次回再試行): " + raceUrl);
            return false;
        }

        String venueName = WebScraper.getVenueName(doc);
        String raceName = WebScraper.getRaceName(doc);
        int raceNum = raceParserService.getRaceNumber(raceUrl);
        String course = WebScraper.getRaceCourse(doc);
        String distance = WebScraper.getRaceDistance(doc);

        List<Horse> horses = raceService.buildHorseList(doc, course, distance, true);

        boolean hasConfirmedResult = horses.stream()
                .anyMatch(horse -> isConfirmedForThisRace(horse, course, distance));

        if (!hasConfirmedResult) {
            System.out.println("結果未確定のためスキップ(次回再試行): " + raceUrl);
            return false;
        }

        int predictionRank = 1;

        for (Horse horse : horses) {
            // コース・距離が一致しない場合、馬の個人ページの最新走が
            // 「まだ行われていないこのレース」ではなく別の過去レースを
            // 指している可能性が高いため、未確定として扱う
            int actualRank = isConfirmedForThisRace(horse, course, distance)
                    ? horse.getActualRace().getRank()
                    : 0;

            raceResultRecordRepository.save(new RaceResultRecord(
                    tracked.getRaceDate(),
                    venueName,
                    raceNum,
                    raceName,
                    horse.getName(),
                    horse.getOdds(),
                    predictionRank,
                    horse.getPredictionScore(),
                    actualRank
            ));

            predictionRank++;
        }

        tracked.setProcessed(true);
        trackedRaceUrlRepository.save(tracked);

        System.out.println("【結果収集】完了: " + raceUrl);

        return true;
    }

    // 馬の個人ページの最新走が本当に「このレース」を指しているかの簡易チェック。
    // レース自体のIDや日付を突き合わせられないため、コースと距離が一致するかで代用する。
    // （表記が「ダート」と「ダ」のように違う場合があるので芝/ダの種別だけで比較する）
    // 障害レースは過去走側のコース判定（extractCourse）が「障」を認識せず空文字に
    // なるため、その場合はコース種別のチェックをスキップし距離のみで判定する
    static boolean isConfirmedForThisRace(Horse horse, String course, String distance) {
        PastRaceInfo actualRace = horse.getActualRace();

        if (actualRace == null || actualRace.getRank() == 0) {
            return false;
        }

        if (!distance.equals(actualRace.getDistance())) {
            return false;
        }

        if (actualRace.getCourse() == null || actualRace.getCourse().isBlank()) {
            return true;
        }

        return sameCourseType(course, actualRace.getCourse());
    }

    static boolean sameCourseType(String a, String b) {
        if (a == null || b == null) {
            return false;
        }

        boolean bothTurf = a.contains("芝") && b.contains("芝");
        boolean bothDirt = a.contains("ダ") && b.contains("ダ");

        return bothTurf || bothDirt;
    }
}

package org.example.keibaapp;

import org.jsoup.nodes.Document;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RaceResultCollectionService {

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

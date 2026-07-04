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
                .anyMatch(horse -> horse.getActualRace() != null && horse.getActualRace().getRank() > 0);

        if (!hasConfirmedResult) {
            System.out.println("結果未確定のためスキップ(次回再試行): " + raceUrl);
            return false;
        }

        int predictionRank = 1;

        for (Horse horse : horses) {
            int actualRank = horse.getActualRace() != null
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
}

package org.example.keibaapp;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class StatusController {

    private final RaceResultCollectionService raceResultCollectionService;
    private final TrackedRaceUrlRepository trackedRaceUrlRepository;
    private final RaceResultRecordRepository raceResultRecordRepository;
    private final RacePayoutRepository racePayoutRepository;
    private final HorseEnrichmentService horseEnrichmentService;
    private final RecentLogBuffer recentLogBuffer;

    public StatusController(
            RaceResultCollectionService raceResultCollectionService,
            TrackedRaceUrlRepository trackedRaceUrlRepository,
            RaceResultRecordRepository raceResultRecordRepository,
            RacePayoutRepository racePayoutRepository,
            HorseEnrichmentService horseEnrichmentService,
            RecentLogBuffer recentLogBuffer) {

        this.raceResultCollectionService = raceResultCollectionService;
        this.trackedRaceUrlRepository = trackedRaceUrlRepository;
        this.raceResultRecordRepository = raceResultRecordRepository;
        this.racePayoutRepository = racePayoutRepository;
        this.horseEnrichmentService = horseEnrichmentService;
        this.recentLogBuffer = recentLogBuffer;
    }

    // 本番のログへ直接アクセスできないため、アプリが標準出力に出した直近の行を返す(再起動で消える)
    @GetMapping(value = "/status/log", produces = "text/plain;charset=UTF-8")
    @ResponseBody
    public String recentLog(@RequestParam(defaultValue = "200") int lines) {
        return String.join("\n", recentLogBuffer.recent(Math.min(Math.max(lines, 1), RecentLogBuffer.MAX_LINES)));
    }

    @GetMapping("/status")
    public String status(Model model) {
        model.addAttribute("circuitRemainingSeconds", WebScraper.getCircuitRemainingSeconds());
        model.addAttribute("isCollecting", raceResultCollectionService.isCollecting());
        model.addAttribute("lastRunAt", raceResultCollectionService.getLastRunAt());
        model.addAttribute("lastRunSummary", raceResultCollectionService.getLastRunSummary());
        model.addAttribute("pendingUrlCount", trackedRaceUrlRepository.countByProcessedFalse());
        model.addAttribute("totalTrackedUrlCount", trackedRaceUrlRepository.count());
        model.addAttribute("totalResultRecords", raceResultRecordRepository.count());
        model.addAttribute("totalPayoutRecords", racePayoutRepository.count());
        model.addAttribute("lastBoardIssue", horseEnrichmentService.getLastBoardIssue());
        return "status";
    }
}

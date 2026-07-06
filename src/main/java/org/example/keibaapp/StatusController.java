package org.example.keibaapp;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StatusController {

    private final RaceResultCollectionService raceResultCollectionService;
    private final TrackedRaceUrlRepository trackedRaceUrlRepository;
    private final RaceResultRecordRepository raceResultRecordRepository;
    private final RacePayoutRepository racePayoutRepository;

    public StatusController(
            RaceResultCollectionService raceResultCollectionService,
            TrackedRaceUrlRepository trackedRaceUrlRepository,
            RaceResultRecordRepository raceResultRecordRepository,
            RacePayoutRepository racePayoutRepository) {

        this.raceResultCollectionService = raceResultCollectionService;
        this.trackedRaceUrlRepository = trackedRaceUrlRepository;
        this.raceResultRecordRepository = raceResultRecordRepository;
        this.racePayoutRepository = racePayoutRepository;
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
        return "status";
    }
}

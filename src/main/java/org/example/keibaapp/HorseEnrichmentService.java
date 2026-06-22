package org.example.keibaapp;

import org.springframework.stereotype.Service;

@Service
public class HorseEnrichmentService {

    private final PredictionService predictionService;
    private final RaceCacheService raceCacheService;

    public HorseEnrichmentService(
            PredictionService predictionService,
            RaceCacheService raceCacheService) {

        this.predictionService = predictionService;
        this.raceCacheService = raceCacheService;
    }

    public HorseDetailInfo getHorseDetail(
            String horseUrl,
            boolean historical) throws InterruptedException {

        String cacheKey = historical
                ? "historical:" + horseUrl
                : "today:" + horseUrl;

        HorseDetailInfo detail =
                raceCacheService.getHorseDetail(cacheKey);

        if (detail == null) {
            Thread.sleep(500);

            detail = historical
                    ? WebScraper.getHistoricalHorseDetailInfo(horseUrl)
                    : WebScraper.getTodayHorseDetailInfo(horseUrl);

            raceCacheService.putHorseDetail(cacheKey, detail);
        }

        return detail;
    }

    public void enrichTodayHorse(Horse horse) throws InterruptedException {

        HorseDetailInfo detail =
                getHorseDetail(horse.getHorseUrl(), false);

        horse.setLastRace(detail.getLastRace());
        horse.setSecondLastRace(detail.getSecondLastRace());
        horse.setThirdLastRace(detail.getThirdLastRace());

        horse.setPredictionScore(
                predictionService.calculateScore(horse));

        horse.setPredictionReason(
                predictionService.createReason(horse));
    }

    public void enrichHistoricalHorse(Horse horse)
            throws InterruptedException {

        HorseDetailInfo detail =
                getHorseDetail(horse.getHorseUrl(), true);

        horse.setLastRace(detail.getLastRace());
        horse.setSecondLastRace(detail.getSecondLastRace());
        horse.setThirdLastRace(detail.getThirdLastRace());
        horse.setActualRace(detail.getActualRace());

        horse.setPredictionScore(
                predictionService.calculateScore(horse));

        horse.setPredictionReason(
                predictionService.createReason(horse));
    }
}
package org.example.keibaapp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HorseEnrichmentServiceTest {

    private final HorseEnrichmentService service =
            new HorseEnrichmentService(
                    new PredictionService(new MarketResidualService()),
                    new RaceCacheService(),
                    new AiPromptService(),
                    new AiService()
            );

    @Test
    void applyRaceModel_shouldDelegateToPredictionServiceAndSetScores() {
        Horse favorite = new Horse("1", "1", "馬1", "騎手1", "57.0", 2.1);
        Horse longshot = new Horse("2", "2", "馬2", "騎手2", "57.0", 50.0);

        service.applyRaceModel(List.of(favorite, longshot));

        assertTrue(favorite.getPredictionScore() > longshot.getPredictionScore());
        assertEquals(1, favorite.getPopularity());
        assertNotNull(favorite.getPredictionReason());
    }
}

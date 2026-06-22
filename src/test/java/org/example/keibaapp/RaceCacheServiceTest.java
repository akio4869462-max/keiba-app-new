package org.example.keibaapp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RaceCacheServiceTest {

    @Test
    void putAndGetHorseDetail() {

        RaceCacheService cacheService =
                new RaceCacheService();

        HorseDetailInfo detail =
                HorseDetailInfo.empty();

        cacheService.putHorseDetail(
                "test",
                detail
        );

        HorseDetailInfo result =
                cacheService.getHorseDetail("test");

        assertEquals(detail, result);
    }
}
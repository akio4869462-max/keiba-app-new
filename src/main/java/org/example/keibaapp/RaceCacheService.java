package org.example.keibaapp;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RaceCacheService {

    private final Map<String, HorseDetailInfo> horseDetailCache = new ConcurrentHashMap<>();

    private List<RaceInfo> cachedRaces;
    private LocalDateTime lastFetchedAt;
    private String cachedRange;

    public HorseDetailInfo getHorseDetail(String key) {
        return horseDetailCache.get(key);
    }

    public void putHorseDetail(String key,
                               HorseDetailInfo detail) {
        horseDetailCache.put(key, detail);
    }

    public boolean isRaceCacheValid(String currentRange) {
        return cachedRaces != null
                && currentRange.equals(cachedRange)
                && lastFetchedAt != null
                && lastFetchedAt.plusMinutes(30)
                .isAfter(LocalDateTime.now());
    }

    public List<RaceInfo> getCachedRaces() {
        return cachedRaces;
    }

    public void cacheRaces(String currentRange,
                           List<RaceInfo> races) {
        this.cachedRaces = races;
        this.cachedRange = currentRange;
        this.lastFetchedAt = LocalDateTime.now();
    }
}
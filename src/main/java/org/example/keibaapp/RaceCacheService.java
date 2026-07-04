package org.example.keibaapp;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RaceCacheService {

    private final Map<String, HorseDetailInfo> horseDetailCache = new ConcurrentHashMap<>();
    private final Map<String, JockeyStats> jockeyStatsCache = new ConcurrentHashMap<>();

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

    public JockeyStats getJockeyStats(String key) {
        return jockeyStatsCache.get(key);
    }

    public void putJockeyStats(String key, JockeyStats stats) {
        jockeyStatsCache.put(key, stats);
    }

    public boolean isRaceCacheValid(String currentRange) {
        return cachedRaces != null
                && currentRange.equals(cachedRange)
                && lastFetchedAt != null
                && lastFetchedAt.plusMinutes(90)
                .isAfter(LocalDateTime.now());
    }

    public List<RaceInfo> getCachedRaces() {
        return cachedRaces;
    }

    // キャッシュにデータが存在するか（有効期限は問わない）
    public boolean hasCachedRaces() {
        return cachedRaces != null && !cachedRaces.isEmpty();
    }

    public void cacheRaces(String currentRange,
                           List<RaceInfo> races) {
        this.cachedRaces = races;
        this.cachedRange = currentRange;
        this.lastFetchedAt = LocalDateTime.now();
    }
}
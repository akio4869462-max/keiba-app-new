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

    private static final int CACHE_TTL_MINUTES = 90;

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
                && lastFetchedAt.plusMinutes(CACHE_TTL_MINUTES)
                .isAfter(LocalDateTime.now());
    }

    public List<RaceInfo> getCachedRaces() {
        return cachedRaces;
    }

    // キャッシュにデータが存在し、かつ十分新しいか。
    // 曜日をハードコードせず毎日通知チェックが走るようになったため、非開催日に
    // 前回開催日の古いキャッシュ(発走時刻だけが今日の現在時刻とたまたま一致する)
    // を使って誤通知しないよう、存在チェックだけでなく鮮度もここで見る
    public boolean hasCachedRaces() {
        return cachedRaces != null
                && !cachedRaces.isEmpty()
                && lastFetchedAt != null
                && lastFetchedAt.plusMinutes(CACHE_TTL_MINUTES)
                .isAfter(LocalDateTime.now());
    }

    public void cacheRaces(String currentRange,
                           List<RaceInfo> races) {
        this.cachedRaces = races;
        this.cachedRange = currentRange;
        this.lastFetchedAt = LocalDateTime.now();
    }

    // テストで古いキャッシュ(前回開催日分等)の挙動を再現するためだけに用意
    // (本番コードからは呼ばない)
    void setLastFetchedAtForTesting(LocalDateTime lastFetchedAt) {
        this.lastFetchedAt = lastFetchedAt;
    }
}
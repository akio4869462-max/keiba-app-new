package org.example.keibaapp;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    // 出馬表(/races)専用のキャッシュ。予想スコア等のエンリッチを行わない
    // 軽量なRaceInfoを保持する(getRaces()側のキャッシュとは別に持つ)
    private List<RaceInfo> cachedBasicRaces;
    private LocalDateTime basicLastFetchedAt;
    private String basicCachedRange;

    // 出馬表(/races)のレース単位キャッシュ。発走を終えたレースの出走内容は
    // 変わらないため、上のリスト単位キャッシュ(TTL 90分)が切れて再取得が走っても、
    // 発走済みのレースだけは当日中ずっとこちらを使い回して再取得しない。
    // キーに日付を含めないため、日付が変わったらクリアする
    private final Map<String, RaceInfo> finishedRaceCache = new ConcurrentHashMap<>();
    private LocalDate finishedRaceCacheDate;

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

    public boolean isBasicRaceCacheValid(String currentRange) {
        return cachedBasicRaces != null
                && currentRange.equals(basicCachedRange)
                && basicLastFetchedAt != null
                && basicLastFetchedAt.plusMinutes(CACHE_TTL_MINUTES)
                .isAfter(LocalDateTime.now());
    }

    public List<RaceInfo> getCachedBasicRaces() {
        return cachedBasicRaces;
    }

    public void cacheBasicRaces(String currentRange, List<RaceInfo> races) {
        this.cachedBasicRaces = races;
        this.basicCachedRange = currentRange;
        this.basicLastFetchedAt = LocalDateTime.now();
    }

    public synchronized RaceInfo getFinishedRace(String venue, int raceNumber) {
        clearFinishedRaceCacheIfStale();
        return finishedRaceCache.get(finishedRaceKey(venue, raceNumber));
    }

    public synchronized void cacheFinishedRace(String venue, int raceNumber, RaceInfo race) {
        clearFinishedRaceCacheIfStale();
        finishedRaceCache.put(finishedRaceKey(venue, raceNumber), race);
    }

    private String finishedRaceKey(String venue, int raceNumber) {
        return venue + "-" + raceNumber;
    }

    private void clearFinishedRaceCacheIfStale() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Tokyo"));
        if (!today.equals(finishedRaceCacheDate)) {
            finishedRaceCache.clear();
            finishedRaceCacheDate = today;
        }
    }

    // テストで古いキャッシュ(前回開催日分等)の挙動を再現するためだけに用意
    // (本番コードからは呼ばない)
    void setLastFetchedAtForTesting(LocalDateTime lastFetchedAt) {
        this.lastFetchedAt = lastFetchedAt;
    }

    // テストで日付をまたいだ際の挙動(レース単位キャッシュのクリア)を
    // 再現するためだけに用意(本番コードからは呼ばない)
    void setFinishedRaceCacheDateForTesting(LocalDate date) {
        this.finishedRaceCacheDate = date;
    }
}
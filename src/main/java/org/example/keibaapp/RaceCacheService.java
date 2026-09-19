package org.example.keibaapp;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RaceCacheService {

    private final Map<String, HorseDetailInfo> horseDetailCache = new ConcurrentHashMap<>();
    private final Map<String, JockeyStats> jockeyStatsCache = new ConcurrentHashMap<>();

    private static final int CACHE_TTL_MINUTES = 90;

    // HTTPスレッドとスケジューラスレッド(通知チェック・定期更新)から同時に読み書きされるためvolatile
    private volatile List<RaceInfo> cachedRaces;
    private volatile LocalDateTime lastFetchedAt;

    // 出馬表(/races)専用のキャッシュ。予想スコア等のエンリッチを行わない
    // 軽量なRaceInfoを保持する(getRaces()側のキャッシュとは別に持つ)
    private volatile List<RaceInfo> cachedBasicRaces;
    private volatile LocalDateTime basicLastFetchedAt;
    private volatile String basicCachedRange;

    // 出馬表(/races)のレース単位キャッシュ。発走を終えたレースの出走内容は
    // 変わらないため、上のリスト単位キャッシュ(TTL 90分)が切れて再取得が走っても、
    // 発走済みのレースだけは当日中ずっとこちらを使い回して再取得しない。
    // キーに日付を含めないため、日付が変わったらクリアする
    private final Map<String, RaceInfo> finishedRaceCache = new ConcurrentHashMap<>();
    private LocalDate finishedRaceCacheDate;

    // 予想(/predict)の締切前オッズ再取得(RaceService.refreshOddsNearPost)で、
    // 同じレースに毎分何度もアクセスしないためのガード。raceUrlは日付を含むため
    // 本来は衝突しないが、無期限に増え続けないよう他のキャッシュと同様に
    // 日付が変わったらクリアする
    private final Set<String> oddsRefreshedUrls = ConcurrentHashMap.newKeySet();
    private LocalDate oddsRefreshedDate;

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

    // 以前は現在時刻を30分単位に丸めた値との一致も条件にしていたが、
    // :00/:30を跨いだ瞬間に必ず無効判定になりTTLが実質機能していなかったため、
    // 鮮度(TTL)のみで判定する
    public boolean isRaceCacheValid() {
        return cachedRaces != null
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

    public void cacheRaces(List<RaceInfo> races) {
        this.cachedRaces = races;
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

    public synchronized boolean wasOddsRefreshed(String raceUrl) {
        clearOddsRefreshedIfStale();
        return oddsRefreshedUrls.contains(raceUrl);
    }

    public synchronized void markOddsRefreshed(String raceUrl) {
        clearOddsRefreshedIfStale();
        oddsRefreshedUrls.add(raceUrl);
    }

    private void clearOddsRefreshedIfStale() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Tokyo"));
        if (!today.equals(oddsRefreshedDate)) {
            oddsRefreshedUrls.clear();
            oddsRefreshedDate = today;
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

    // テストで日付をまたいだ際の挙動(オッズ再取得済みフラグのクリア)を
    // 再現するためだけに用意(本番コードからは呼ばない)
    void setOddsRefreshedDateForTesting(LocalDate date) {
        this.oddsRefreshedDate = date;
    }
}
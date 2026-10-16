package org.example.keibaapp;

import org.jsoup.HttpStatusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TrackBiasServiceTest {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
    // 2026-07-12 小倉の開催日(先頭8桁が共通。末尾2桁が1〜12R)
    private static final String DAY = "26100206";
    private static final LocalDate RACE_DAY = LocalDate.of(2026, 7, 12);
    private static final Clock CLOCK = Clock.fixed(
            LocalDate.of(2026, 10, 17).atTime(9, 0).atZone(JST).toInstant(), JST);

    private final Map<String, RaceBiasStat> stored = new HashMap<>();
    private final Map<String, ResultPageInfo> pages = new HashMap<>();
    private final List<String> fetched = new ArrayList<>();
    private final List<String> failing = new ArrayList<>();
    private RaceBiasStatRepository repository;
    private TrackBiasService service;

    @BeforeEach
    void setUp() {
        repository = mock(RaceBiasStatRepository.class);
        when(repository.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(stored.get(inv.<String>getArgument(0))));
        when(repository.save(any())).thenAnswer(inv -> {
            RaceBiasStat stat = inv.getArgument(0);
            stored.put(stat.getRaceId(), stat);
            return stat;
        });

        service = new TrackBiasService(repository, raceId -> {
            fetched.add(raceId);

            if (failing.contains(raceId)) {
                throw new IOException("network error");
            }

            ResultPageInfo page = pages.get(raceId);

            if (page == null) {
                throw new HttpStatusException("Not Found", 404, raceId);
            }

            return page;
        }, CLOCK);
        service.setPriors(Map.of("小倉|芝", -0.02, "_default", -0.01));
    }

    // 10頭立て。外(馬番が大きい)ほど外を回った(lat)、着順は skew に応じてずらす
    private ResultPageInfo page(String surface, int skew) {
        List<ResultPageInfo.ResultRow> rows = new ArrayList<>();
        Map<Integer, Double> lat = new HashMap<>();

        for (int i = 1; i <= 10; i++) {
            int rank = ((i - 1 + skew) % 10) + 1;
            rows.add(new ResultPageInfo.ResultRow(i, rank, 2.0 + i * 1.5));
            lat.put(i, (double) (i % 4));
        }

        return new ResultPageInfo(RACE_DAY, "小倉", surface, rows, lat);
    }

    private void setUpFullDay(String surface) {
        for (int r = 1; r <= 12; r++) {
            pages.put(DAY + String.format("%02d", r), page(surface, r));
        }
    }

    private PastRaceInfo lastRace(String raceId, String umaban) {
        PastRaceInfo past = new PastRaceInfo("前走", 3, "OP", 2);
        past.setRaceUrl("https://sports.yahoo.co.jp/keiba/race/index/" + raceId);
        past.setUmaban(umaban);
        return past;
    }

    @Test
    void featuresFor_shouldComputeLatCAndHeadwindFromTheWholeRacingDay() {
        setUpFullDay("芝");

        TrackBiasService.Features features = service.featuresFor(lastRace(DAY + "03", "7"), true).orElseThrow();

        // p_lat_c = その馬の前走lat - 前走レースの平均lat
        ResultPageInfo target = pages.get(DAY + "03");
        TrackBiasCalculator.RaceBias targetBias = TrackBiasCalculator.raceBias(target.rows(), target.latMeans());
        double expectedLatC = target.latMeans().get(7) - targetBias.meanLat();
        assertEquals(expectedLatC, features.pLatC(), 1e-9);

        // p_hw_lat = (自レースを除く同日・同芝ダの他レースから縮小推定した当日バイアス) × (-p_lat_c)
        List<TrackBiasCalculator.RaceBias> others = new ArrayList<>();
        for (int r = 1; r <= 12; r++) {
            if (r == 3) {
                continue;
            }
            ResultPageInfo p = pages.get(DAY + String.format("%02d", r));
            others.add(TrackBiasCalculator.raceBias(p.rows(), p.latMeans()));
        }
        double expectedDayBias = TrackBiasCalculator.dayBias(others, -0.02);
        assertEquals(expectedDayBias * -expectedLatC, features.pHwLat(), 1e-9);
    }

    @Test
    void featuresFor_shouldFetchEachRaceOnlyOnce() {
        setUpFullDay("芝");

        service.featuresFor(lastRace(DAY + "03", "7"), true);
        int firstRunFetches = fetched.size();
        service.featuresFor(lastRace(DAY + "03", "2"), true);
        service.featuresFor(lastRace(DAY + "05", "4"), true);

        assertEquals(12, firstRunFetches);
        assertEquals(12, fetched.size());
        // 結果は永続化される(再起動後も再取得しない)
        assertEquals(12, stored.size());
    }

    @Test
    void featuresFor_shouldNotFetchWhenNotAllowedAndNothingStored() {
        setUpFullDay("芝");

        assertTrue(service.featuresFor(lastRace(DAY + "03", "7"), false).isEmpty());
        assertTrue(fetched.isEmpty());
    }

    @Test
    void featuresFor_shouldUseStoredStatsWithoutFetchingWhenNotAllowed() {
        setUpFullDay("芝");
        service.featuresFor(lastRace(DAY + "03", "7"), true);
        fetched.clear();

        // 結果収集のポーリング(通信禁止モード)でも、取得済みの統計から同じ値が得られる
        assertTrue(service.featuresFor(lastRace(DAY + "03", "7"), false).isPresent());
        assertTrue(fetched.isEmpty());
    }

    @Test
    void featuresFor_shouldReturnEmptyForJumpRace() {
        setUpFullDay("障害");

        assertTrue(service.featuresFor(lastRace(DAY + "03", "7"), true).isEmpty());
    }

    @Test
    void featuresFor_shouldIgnoreRacesOnTheOtherSurfaceWhenEstimatingDayBias() {
        setUpFullDay("芝");
        // 5Rだけダート: 芝の当日バイアスの推定に含めない(prior付きの計算結果が変わる)
        pages.put(DAY + "05", page("ダ", 5));
        TrackBiasService.Features withDirtRace = service.featuresFor(lastRace(DAY + "03", "7"), true).orElseThrow();

        List<TrackBiasCalculator.RaceBias> turfOthers = new ArrayList<>();
        for (int r = 1; r <= 12; r++) {
            if (r == 3 || r == 5) {
                continue;
            }
            ResultPageInfo p = pages.get(DAY + String.format("%02d", r));
            turfOthers.add(TrackBiasCalculator.raceBias(p.rows(), p.latMeans()));
        }
        double latC = withDirtRace.pLatC();
        assertEquals(TrackBiasCalculator.dayBias(turfOthers, -0.02) * -latC, withDirtRace.pHwLat(), 1e-9);
    }

    @Test
    void featuresFor_shouldTreatNonexistentRaceAsAbsentAndStillCompute() {
        setUpFullDay("芝");
        pages.remove(DAY + "12");   // 404(その開催日にそのレースは無い)

        assertTrue(service.featuresFor(lastRace(DAY + "03", "7"), true).isPresent());

        // 存在しないことも記録され、次回以降は取得しない
        fetched.clear();
        service.featuresFor(lastRace(DAY + "04", "7"), true);
        assertTrue(fetched.isEmpty());
    }

    @Test
    void featuresFor_shouldReturnEmptyAndNotRetryImmediatelyWhenFetchFails() {
        setUpFullDay("芝");
        failing.add(DAY + "09");

        assertTrue(service.featuresFor(lastRace(DAY + "03", "7"), true).isEmpty());

        int fetchesAfterFailure = fetched.size();
        assertTrue(service.featuresFor(lastRace(DAY + "03", "7"), true).isEmpty());

        // 失敗したレースを毎回取りに行かない(サイト障害中に負荷をかけない)
        assertEquals(fetchesAfterFailure, fetched.size());
    }

    @Test
    void featuresFor_shouldReturnEmptyWhenHorseHasNoLatInThatRace() {
        setUpFullDay("芝");

        assertTrue(service.featuresFor(lastRace(DAY + "03", "99"), true).isEmpty());
    }

    @Test
    void featuresFor_shouldReturnEmptyForMissingOrInvalidPastRace() {
        assertTrue(service.featuresFor(null, true).isEmpty());
        assertTrue(service.featuresFor(PastRaceInfo.empty(), true).isEmpty());

        PastRaceInfo noUmaban = new PastRaceInfo("前走", 3, "OP", 2);
        noUmaban.setRaceUrl("https://sports.yahoo.co.jp/keiba/race/index/" + DAY + "03");
        assertTrue(service.featuresFor(noUmaban, true).isEmpty());
        assertTrue(fetched.isEmpty());
    }

    @Test
    void getStat_shouldNotPersistRaceHeldTodayOrLater() {
        // 当日以降のレースは結果欄が未確定(古い内容が出ている)可能性があるため保存しない
        ResultPageInfo today = new ResultPageInfo(LocalDate.of(2026, 10, 17), "東京", "芝",
                page("芝", 1).rows(), page("芝", 1).latMeans());
        pages.put("2605040101", today);

        assertNotNull(service.getStat("2605040101", true));
        assertTrue(stored.isEmpty());
    }

    @Test
    void extractRaceId_shouldAcceptJraUrlsOnly() {
        assertEquals("2609040504", TrackBiasService.extractRaceId(
                "https://sports.yahoo.co.jp/keiba/race/result/2609040504"));
        assertEquals("2609040504", TrackBiasService.extractRaceId(
                "https://sports.yahoo.co.jp/keiba/race/index/2609040504"));
        assertEquals("2609040504", TrackBiasService.extractRaceId(
                "https://sports.yahoo.co.jp/keiba/race/denma/2609040504"));
        // 開催場コードがJRA(01〜10)でないもの(地方・海外)は対象外
        assertNull(TrackBiasService.extractRaceId("https://sports.yahoo.co.jp/keiba/race/index/2646040504"));
        assertNull(TrackBiasService.extractRaceId("https://sports.yahoo.co.jp/keiba/directory/horse/2019100675/"));
    }

    @Test
    void priorFor_shouldFallBackToDefaultForUnknownVenue() {
        assertEquals(-0.02, service.priorFor("小倉", "芝"), 1e-12);
        assertEquals(-0.01, service.priorFor("未知", "芝"), 1e-12);
    }
}

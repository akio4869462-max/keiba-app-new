package org.example.keibaapp;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 画面（Thymeleaf）とは独立してデータをJSONで提供するREST APIコントローラ。
 *
 * <p>エンドポイントは /api/v1 配下に集約し、既存のMVCコントローラ（RaceController等）とは
 * 役割を分離しています。ドキュメントはspringdocが自動生成し、/swagger-ui.html で確認できます。</p>
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Keiba API", description = "出馬表・予想・バックテスト統計・お気に入り管理のREST API")
public class ApiController {

    private final RaceService raceService;
    private final RaceResultRecordRepository raceResultRecordRepository;
    private final RaceResultStatsService raceResultStatsService;
    private final FavoriteHorseService favoriteHorseService;
    private final FavoriteJockeyService favoriteJockeyService;

    public ApiController(
            RaceService raceService,
            RaceResultRecordRepository raceResultRecordRepository,
            RaceResultStatsService raceResultStatsService,
            FavoriteHorseService favoriteHorseService,
            FavoriteJockeyService favoriteJockeyService) {
        this.raceService = raceService;
        this.raceResultRecordRepository = raceResultRecordRepository;
        this.raceResultStatsService = raceResultStatsService;
        this.favoriteHorseService = favoriteHorseService;
        this.favoriteJockeyService = favoriteJockeyService;
    }

    // ====================================================
    // 📄 DTO（APIのレスポンス形式をエンティティから切り離すためのレコード群）
    // ====================================================

    /** 出馬表1レース分のレスポンス */
    public record RaceDto(
            int raceNum,
            String venue,
            String raceName,
            LocalTime raceTime,
            String course,
            String distance,
            List<HorseDto> horses) {
    }

    /** 出走馬1頭分のレスポンス（予想スコアは /predictions のみ意味を持つ） */
    public record HorseDto(
            String waku,
            String umaban,
            String name,
            String jockeyName,
            double odds,
            double predictionScore,
            String predictionReason) {
    }

    /** バックテスト統計のレスポンス */
    public record BacktestStatsDto(
            int raceCount,
            double top1WinRate,
            double top1Top3Rate,
            double top3Top3Rate,
            double roi,
            double totalReturn,
            List<RaceResultStatsService.OddsBandStat> oddsBandStats,
            List<RaceResultStatsService.ScoreBandStat> scoreBandStats,
            List<RaceResultStatsService.WeeklyStat> weeklyStats) {
    }

    /** お気に入り登録リクエスト（馬・騎手共通） */
    public record FavoriteRequest(String name) {
    }

    /** お気に入り1件分のレスポンス（馬・騎手共通） */
    public record FavoriteDto(Long id, String name) {
    }

    // ====================================================
    // 🐎 出馬表・予想
    // ====================================================

    @GetMapping("/races")
    @Operation(summary = "本日の出馬表一覧",
            description = "過去走の取得を行わない軽量版。予想スコアは含まれません。")
    public List<RaceDto> getRaces() {
        return toRaceDtos(raceService.getBasicRaces());
    }

    @GetMapping("/predictions")
    @Operation(summary = "予想スコア付きの出馬表一覧",
            description = "各馬の過去走を取得して予想スコアを算出します。"
                    + "初回はスクレイピングが走るため応答に時間がかかります（結果は30分キャッシュ）。")
    public List<RaceDto> getPredictions() {
        return toRaceDtos(raceService.getRaces());
    }

    private List<RaceDto> toRaceDtos(List<RaceInfo> races) {
        return races.stream()
                .map(race -> new RaceDto(
                        race.getRaceNum(),
                        race.getVenue(),
                        race.getRaceName(),
                        race.getRaceTime(),
                        race.getCourse(),
                        race.getDistance(),
                        race.getHorses().stream()
                                // スコア降順（未算出時は0で同順のため元の馬番順が保たれる）
                                .sorted(Comparator.comparingDouble(Horse::getPredictionScore).reversed())
                                .map(h -> new HorseDto(
                                        h.getWaku(),
                                        h.getUmaban(),
                                        h.getName(),
                                        h.getJockeyName(),
                                        h.getOdds(),
                                        h.getPredictionScore(),
                                        h.getPredictionReason()))
                                .collect(Collectors.toList())))
                .collect(Collectors.toList());
    }

    // ====================================================
    // 📊 バックテスト統計
    // ====================================================

    @GetMapping("/results/stats")
    @Operation(summary = "予想バックテストの統計サマリー",
            description = "収集済みのレース結果をもとに、本命（予想1位）の勝率・複勝率・回収率、"
                    + "オッズ帯別・スコア帯別・週別の成績を返します。")
    public BacktestStatsDto getBacktestStats() {
        List<RaceResultRecord> allRecords = raceResultRecordRepository.findAll();

        List<RaceResultRecord> topPicks = allRecords.stream()
                .filter(r -> r.getPredictionRank() == 1)
                .collect(Collectors.toList());
        List<RaceResultRecord> top3Picks = allRecords.stream()
                .filter(r -> r.getPredictionRank() <= 3)
                .collect(Collectors.toList());

        int raceCount = topPicks.size();

        return new BacktestStatsDto(
                raceCount,
                raceResultStatsService.rate(raceResultStatsService.countWins(topPicks), raceCount),
                raceResultStatsService.rate(raceResultStatsService.countTop3(topPicks), raceCount),
                raceResultStatsService.rate(raceResultStatsService.countTop3(top3Picks), top3Picks.size()),
                raceResultStatsService.calculateRoi(topPicks),
                raceResultStatsService.totalReturn(topPicks),
                raceResultStatsService.buildOddsBandStats(topPicks),
                raceResultStatsService.buildScoreBandStats(allRecords),
                raceResultStatsService.buildWeeklyStats(topPicks));
    }

    // ====================================================
    // ⭐ お気に入り馬
    // ====================================================

    @GetMapping("/favorites/horses")
    @Operation(summary = "お気に入り馬の一覧")
    public List<FavoriteDto> getFavoriteHorses() {
        return favoriteHorseService.findAll().stream()
                .map(f -> new FavoriteDto(f.getId(), f.getHorseName()))
                .collect(Collectors.toList());
    }

    @PostMapping("/favorites/horses")
    @Operation(summary = "お気に入り馬の登録", description = "同名の馬が既に登録済みの場合は既存レコードを返します（重複登録なし）。")
    public ResponseEntity<FavoriteDto> addFavoriteHorse(@RequestBody FavoriteRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        FavoriteHorse saved = favoriteHorseService.save(request.name().trim());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new FavoriteDto(saved.getId(), saved.getHorseName()));
    }

    @DeleteMapping("/favorites/horses/{id}")
    @Operation(summary = "お気に入り馬の削除")
    public ResponseEntity<Void> deleteFavoriteHorse(@PathVariable Long id) {
        favoriteHorseService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ====================================================
    // 🏇 お気に入り騎手
    // ====================================================

    @GetMapping("/favorites/jockeys")
    @Operation(summary = "お気に入り騎手の一覧")
    public List<FavoriteDto> getFavoriteJockeys() {
        return favoriteJockeyService.findAll().stream()
                .map(f -> new FavoriteDto(f.getId(), f.getJockeyName()))
                .collect(Collectors.toList());
    }

    @PostMapping("/favorites/jockeys")
    @Operation(summary = "お気に入り騎手の登録", description = "同名の騎手が既に登録済みの場合は既存レコードを返します（重複登録なし）。")
    public ResponseEntity<FavoriteDto> addFavoriteJockey(@RequestBody FavoriteRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        FavoriteJockey saved = favoriteJockeyService.save(request.name().trim());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new FavoriteDto(saved.getId(), saved.getJockeyName()));
    }

    @DeleteMapping("/favorites/jockeys/{id}")
    @Operation(summary = "お気に入り騎手の削除")
    public ResponseEntity<Void> deleteFavoriteJockey(@PathVariable Long id) {
        favoriteJockeyService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

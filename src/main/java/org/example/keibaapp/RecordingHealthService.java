package org.example.keibaapp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 前向き記録(朝オッズ・10分前・3分前のスナップショット)が欠けていないかを見る自己監視。
//
// 背景: 2026-10-29にサーバーが止まり、誰も気づかないまま約2日たった。記録が静かに止まるのを早く知るため、
// 開催日に30分おきに確認し、欠けていればDiscordに通知する。/status にも今日の記録の状況を出す。
//
// 判定(いずれも開催日のみ。開催のレース数は、朝オッズの記録時に一覧から数えた値か、記録済みの数):
//  - 朝オッズ: 9:30以降に、開催のレース数より記録が少ない
//  - 10分前: 発走から20分たっても記録が無いレースがある(キャッシュ上のレースが対象)
//  - 3分前: 発走から10分たっても記録が無いレースがある
//  - 10:30以降に10分前の記録が1件も無い(キャッシュが空で、そもそも対象が見えない場合の保険)
// 同じ異常は1回だけ通知し、内容が変わったときだけ再通知する。
@Service
public class RecordingHealthService {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    static final LocalTime MORNING_CHECK_FROM = LocalTime.of(9, 30);
    static final LocalTime NO_SNAPSHOT_CHECK_FROM = LocalTime.of(10, 30);
    static final int TEN_MINUTE_GRACE_MINUTES = 20;
    static final int CLOSE_GRACE_MINUTES = 10;
    private static final int MAX_LISTED = 8;

    public record Summary(LocalDate date, int expectedRaces, int morningRaces, int tenMinuteRaces,
                          int closeRaces, LocalDateTime lastSnapshotAt, LocalDateTime lastMorningAt) {
    }

    private final OddsSnapshotRepository snapshotRepository;
    private final MorningOddsRepository morningOddsRepository;
    private final RaceCacheService raceCacheService;
    private final MorningOddsService morningOddsService;
    private final DiscordNotificationService discordNotificationService;
    private final Clock clock;

    private LocalDate lastAlertDate;
    private String lastAlertSignature;

    @Autowired
    public RecordingHealthService(OddsSnapshotRepository snapshotRepository,
                                  MorningOddsRepository morningOddsRepository,
                                  RaceCacheService raceCacheService,
                                  MorningOddsService morningOddsService,
                                  DiscordNotificationService discordNotificationService) {
        this(snapshotRepository, morningOddsRepository, raceCacheService, morningOddsService,
                discordNotificationService, Clock.system(JST));
    }

    RecordingHealthService(OddsSnapshotRepository snapshotRepository,
                           MorningOddsRepository morningOddsRepository,
                           RaceCacheService raceCacheService,
                           MorningOddsService morningOddsService,
                           DiscordNotificationService discordNotificationService,
                           Clock clock) {
        this.snapshotRepository = snapshotRepository;
        this.morningOddsRepository = morningOddsRepository;
        this.raceCacheService = raceCacheService;
        this.morningOddsService = morningOddsService;
        this.discordNotificationService = discordNotificationService;
        this.clock = clock;
    }

    public Summary summarize() {
        return summarize(LocalDate.now(clock));
    }

    private Summary summarize(LocalDate today) {
        int morning = keys(morningOddsRepository.findRaceKeys(today)).size();

        return new Summary(
                today,
                Math.max(morningOddsService.getTodayRaceCount(today), morning),
                morning,
                keys(snapshotRepository.findTenMinuteRaceKeys(today)).size(),
                keys(snapshotRepository.findCloseRaceKeys(today)).size(),
                snapshotRepository.findTopByOrderByCapturedAtDesc().map(OddsSnapshot::getCapturedAt).orElse(null),
                morningOddsRepository.findTopByOrderByCapturedAtDesc().map(MorningOdds::getCapturedAt).orElse(null));
    }

    // (開催場, レース番号) → "開催場|番号"。旧データの開催場名の空白に左右されないよう trim する
    private static Set<String> keys(List<Object[]> rows) {
        Set<String> keys = new HashSet<>();

        for (Object[] row : rows) {
            keys.add(String.valueOf(row[0]).trim() + "|" + row[1]);
        }

        return keys;
    }

    // 今の時点の問題点(無ければ空)。開催日でなければ空
    List<String> findProblems(LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        Summary summary = summarize(today);
        List<String> problems = new ArrayList<>();

        if (summary.expectedRaces() == 0) {
            return problems;
        }

        LocalTime time = now.toLocalTime();

        if (!time.isBefore(MORNING_CHECK_FROM) && summary.morningRaces() < summary.expectedRaces()) {
            problems.add("朝オッズの記録が" + summary.morningRaces() + " / " + summary.expectedRaces()
                    + "レースしかありません");
        }

        if (!time.isBefore(NO_SNAPSHOT_CHECK_FROM) && summary.tenMinuteRaces() == 0) {
            problems.add("10分前の記録が1件もありません(一覧の読み込みや取得に問題がある可能性)");
        }

        Set<String> tenMinute = keys(snapshotRepository.findTenMinuteRaceKeys(today));
        Set<String> close = keys(snapshotRepository.findCloseRaceKeys(today));
        List<String> missingTen = new ArrayList<>();
        List<String> missingClose = new ArrayList<>();

        List<RaceInfo> races = raceCacheService.getCachedRaces();

        if (races != null) {
            for (RaceInfo race : races) {
                if (race.getRaceTime() == null || race.getVenue() == null) {
                    continue;
                }

                long minutesAfterPost = Duration.between(race.getRaceTime(), time).toMinutes();
                String key = race.getVenue().trim() + "|" + race.getRaceNum();
                String label = race.getVenue().trim() + race.getRaceNum() + "R";

                if (minutesAfterPost >= TEN_MINUTE_GRACE_MINUTES && !tenMinute.contains(key)) {
                    missingTen.add(label);
                }

                if (minutesAfterPost >= CLOSE_GRACE_MINUTES && !close.contains(key)) {
                    missingClose.add(label);
                }
            }
        }

        addMissing(problems, "10分前の記録が無いレース", missingTen);
        addMissing(problems, "3分前の記録が無いレース", missingClose);

        return problems;
    }

    private static void addMissing(List<String> problems, String title, List<String> labels) {
        if (labels.isEmpty()) {
            return;
        }

        List<String> shown = labels.subList(0, Math.min(MAX_LISTED, labels.size()));
        String more = labels.size() > MAX_LISTED ? " ほか" + (labels.size() - MAX_LISTED) + "レース" : "";
        problems.add(title + ": " + String.join(", ", shown) + more);
    }

    // 開催日の9:05〜17:35に30分おきに確認する(曜日はハードコードせず、開催のレース数で開催日かを判定)
    @Scheduled(cron = "0 5,35 9-17 * * *", zone = "Asia/Tokyo")
    public void checkAndAlert() {
        try {
            check(LocalDateTime.now(clock));
        } catch (Exception e) {
            System.out.println("【記録の自己監視】確認に失敗: " + e.getMessage());
        }
    }

    // テストしやすいよう、時刻を引数にした本体
    synchronized void check(LocalDateTime now) {
        LocalDate today = now.toLocalDate();

        if (!today.equals(lastAlertDate)) {
            lastAlertDate = today;
            lastAlertSignature = null;
        }

        List<String> problems = findProblems(now);

        if (problems.isEmpty()) {
            lastAlertSignature = null;      // 解消したら、次に同じ異常が起きたときまた通知する
            return;
        }

        String signature = String.join("\n", problems);

        if (signature.equals(lastAlertSignature)) {
            return;
        }

        lastAlertSignature = signature;
        String message = "🚨 前向き記録に欠けがあります (" + now.toLocalTime().withSecond(0).withNano(0) + ")\n" + signature
                + "\n(/status/log と /status で原因を確認してください)";
        System.out.println(message);

        try {
            discordNotificationService.sendMessage(message);
        } catch (Exception e) {
            lastAlertSignature = null;      // 送信に失敗したら、次の確認で再送する
            System.out.println("【記録の自己監視】Discord通知に失敗: " + e.getMessage());
        }
    }
}

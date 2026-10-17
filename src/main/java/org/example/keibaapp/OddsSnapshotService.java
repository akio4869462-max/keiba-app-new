package org.example.keibaapp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 発走10分前の再取得で得た予想を馬ごとに保存する(OddsSnapshot)。保存の失敗が
// 予想表示の更新を巻き込まないよう、呼び出し側(RaceService)は例外を握りつぶす
@Service
public class OddsSnapshotService {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    private final OddsSnapshotRepository repository;
    private final Clock clock;

    @Autowired
    public OddsSnapshotService(OddsSnapshotRepository repository) {
        this(repository, Clock.system(JST));
    }

    OddsSnapshotService(OddsSnapshotRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    // previousHorsesは再取得前(直前の一覧取得時点)の馬、currentHorsesは再取得・モデル再計算後の馬。
    // 同じレースは1日1回だけ保存する(再取得自体が1日1回のガード付きだが念のため)
    public int record(RaceInfo race, List<Horse> previousHorses, List<Horse> currentHorses) {
        LocalDateTime now = LocalDateTime.now(clock);

        if (race.getRaceNum() <= 0 || race.getVenue() == null
                || repository.existsByRaceDateAndVenueAndRaceNumber(now.toLocalDate(), race.getVenue(), race.getRaceNum())) {
            return 0;
        }

        Map<String, Double> earlierOdds = new HashMap<>();
        if (previousHorses != null) {
            for (Horse horse : previousHorses) {
                if (horse.getOdds() > 0 && horse.getOdds() < 999.9) {
                    earlierOdds.put(horse.getUmaban(), horse.getOdds());
                }
            }
        }

        List<OddsSnapshot> snapshots = new ArrayList<>();

        for (Horse horse : currentHorses) {
            // オッズが無効な馬(取消・除外)と馬番が読めない馬は保存しない
            if (horse.getPopularity() <= 0 || horse.getUmaban() == null
                    || !horse.getUmaban().trim().matches("\\d+")) {
                continue;
            }

            snapshots.add(new OddsSnapshot(
                    now.toLocalDate(), race.getVenue(), race.getRaceNum(),
                    Integer.parseInt(horse.getUmaban().trim()), horse.getName(),
                    horse.getOdds(), earlierOdds.get(horse.getUmaban()), horse.getFukuGap(),
                    horse.getPredictionScore(), horse.getMarketProbability(), horse.getOverlay(),
                    horse.getPopularity(), horse.isRecommended(), PredictionService.MODEL_VERSION, now)
                    .withPoolShares(horse.getUmarenShare(), horse.getWideShare(), horse.getExactaFirstShare()));
        }

        repository.saveAll(snapshots);

        return snapshots.size();
    }
}

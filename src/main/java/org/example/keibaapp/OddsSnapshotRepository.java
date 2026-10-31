package org.example.keibaapp;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface OddsSnapshotRepository extends JpaRepository<OddsSnapshot, Long> {

    boolean existsByRaceDateAndVenueAndRaceNumber(LocalDate raceDate, String venue, int raceNumber);

    boolean existsByRaceDateAndVenueAndRaceNumberAndPhase(LocalDate raceDate, String venue, int raceNumber, String phase);

    // 今日、10分前(段階なしの旧行を含む)の記録があるレース(開催場, レース番号)。記録の自己監視に使う
    @Query("select distinct s.venue, s.raceNumber from OddsSnapshot s "
            + "where s.raceDate = :date and (s.phase is null or s.phase = 'T10')")
    List<Object[]> findTenMinuteRaceKeys(@Param("date") LocalDate date);

    // 今日、3分前(段階"T3")の記録があるレース
    @Query("select distinct s.venue, s.raceNumber from OddsSnapshot s where s.raceDate = :date and s.phase = 'T3'")
    List<Object[]> findCloseRaceKeys(@Param("date") LocalDate date);

    Optional<OddsSnapshot> findTopByOrderByCapturedAtDesc();
}

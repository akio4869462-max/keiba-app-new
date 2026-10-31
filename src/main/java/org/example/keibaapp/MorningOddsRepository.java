package org.example.keibaapp;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MorningOddsRepository extends JpaRepository<MorningOdds, Long> {

    boolean existsByRaceDateAndVenueAndRaceNumber(LocalDate raceDate, String venue, int raceNumber);

    List<MorningOdds> findByRaceDateAndVenueAndRaceNumber(LocalDate raceDate, String venue, int raceNumber);

    // 今日、朝オッズを記録したレース(開催場, レース番号)
    @Query("select distinct m.venue, m.raceNumber from MorningOdds m where m.raceDate = :date")
    List<Object[]> findRaceKeys(@Param("date") LocalDate date);

    Optional<MorningOdds> findTopByOrderByCapturedAtDesc();
}

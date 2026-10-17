package org.example.keibaapp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MorningOddsRepository extends JpaRepository<MorningOdds, Long> {

    boolean existsByRaceDateAndVenueAndRaceNumber(LocalDate raceDate, String venue, int raceNumber);

    List<MorningOdds> findByRaceDateAndVenueAndRaceNumber(LocalDate raceDate, String venue, int raceNumber);
}

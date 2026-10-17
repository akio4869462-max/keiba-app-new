package org.example.keibaapp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface OddsSnapshotRepository extends JpaRepository<OddsSnapshot, Long> {

    boolean existsByRaceDateAndVenueAndRaceNumber(LocalDate raceDate, String venue, int raceNumber);

    boolean existsByRaceDateAndVenueAndRaceNumberAndPhase(LocalDate raceDate, String venue, int raceNumber, String phase);
}

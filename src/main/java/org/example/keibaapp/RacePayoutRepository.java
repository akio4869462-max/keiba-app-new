package org.example.keibaapp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface RacePayoutRepository extends JpaRepository<RacePayout, Long> {

    List<RacePayout> findByRaceDateAndVenueAndRaceNumber(LocalDate raceDate, String venue, int raceNumber);

    List<RacePayout> findByRaceDateGreaterThanEqual(LocalDate date);
}

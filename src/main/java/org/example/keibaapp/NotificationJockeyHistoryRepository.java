package org.example.keibaapp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface NotificationJockeyHistoryRepository
        extends JpaRepository<NotificationJockeyHistory, Long> {

    boolean existsByJockeyNameAndVenueAndRaceNumberAndRaceDate(
            String jockeyName,
            String venue,
            int raceNumber,
            LocalDate raceDate);
}

package org.example.keibaapp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface NotificationHistoryRepository
        extends JpaRepository<NotificationHistory, Long> {

    boolean existsByHorseNameAndVenueAndRaceNumberAndRaceDate(
            String horseName,
            String venue,
            int raceNumber,
            LocalDate raceDate);
}

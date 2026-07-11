package org.example.keibaapp;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;

public interface NotificationHistoryRepository
        extends JpaRepository<NotificationHistory, Long> {

    Optional<NotificationHistory>
    findByHorseNameAndRaceNameAndRaceDate(
            String horseName,
            String raceName,
            LocalDate raceDate);
}
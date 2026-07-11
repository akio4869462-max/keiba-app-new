package org.example.keibaapp;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;

public interface NotificationJockeyHistoryRepository
        extends JpaRepository<NotificationJockeyHistory, Long> {

    Optional<NotificationJockeyHistory>
    findByJockeyNameAndRaceNameAndRaceDate(
            String jockeyName,
            String raceName,
            LocalDate raceDate);
}
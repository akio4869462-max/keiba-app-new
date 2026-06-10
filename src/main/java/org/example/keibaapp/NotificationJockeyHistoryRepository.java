package org.example.keibaapp;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface NotificationJockeyHistoryRepository
        extends JpaRepository<NotificationJockeyHistory, Long> {

    Optional<NotificationJockeyHistory>
    findByJockeyNameAndRaceName(
            String jockeyName,
            String raceName);
}
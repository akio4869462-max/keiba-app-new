package org.example.keibaapp;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface NotificationHistoryRepository
        extends JpaRepository<NotificationHistory, Long> {

    Optional<NotificationHistory>
    findByHorseNameAndRaceName(
            String horseName,
            String raceName);
}
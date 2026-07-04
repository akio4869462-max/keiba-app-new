package org.example.keibaapp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrackedRaceUrlRepository
        extends JpaRepository<TrackedRaceUrl, Long> {

    Optional<TrackedRaceUrl> findByRaceUrl(String raceUrl);

    List<TrackedRaceUrl> findByProcessedFalse();
}

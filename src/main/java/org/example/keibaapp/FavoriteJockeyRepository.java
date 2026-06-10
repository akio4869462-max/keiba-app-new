package org.example.keibaapp;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface FavoriteJockeyRepository
        extends JpaRepository<FavoriteJockey, Long> {

    Optional<FavoriteJockey> findByJockeyName(String jockeyName);
}
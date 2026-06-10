package org.example.keibaapp;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface FavoriteHorseRepository
        extends JpaRepository<FavoriteHorse, Long> {

    Optional<FavoriteHorse> findByHorseName(String horseName);
}
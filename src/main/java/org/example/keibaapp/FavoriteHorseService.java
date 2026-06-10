package org.example.keibaapp;

import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class FavoriteHorseService {

    private final FavoriteHorseRepository repository;

    public FavoriteHorseService(FavoriteHorseRepository repository) {
        this.repository = repository;
    }

    public FavoriteHorse save(String horseName) {
        return repository.findByHorseName(horseName)
                .orElseGet(() -> repository.save(new FavoriteHorse(horseName)));
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    public List<FavoriteHorse> findAll() {
        return repository.findAll();
    }
}
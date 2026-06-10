package org.example.keibaapp;

import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class FavoriteJockeyService {

    private final FavoriteJockeyRepository repository;

    public FavoriteJockeyService(FavoriteJockeyRepository repository) {
        this.repository = repository;
    }

    public FavoriteJockey save(String jockeyName) {
        return repository.findByJockeyName(jockeyName)
                .orElseGet(() -> repository.save(new FavoriteJockey(jockeyName)));
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    public List<FavoriteJockey> findAll() {
        return repository.findAll();
    }
}
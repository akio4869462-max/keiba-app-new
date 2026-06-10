package org.example.keibaapp;

import jakarta.persistence.*;

@Entity
public class FavoriteHorse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String horseName;

    public FavoriteHorse() {
    }

    public FavoriteHorse(String horseName) {
        this.horseName = horseName;
    }

    public Long getId() {
        return id;
    }

    public String getHorseName() {
        return horseName;
    }

    public void setHorseName(String horseName) {
        this.horseName = horseName;
    }
}
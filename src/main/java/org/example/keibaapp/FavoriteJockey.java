package org.example.keibaapp;

import jakarta.persistence.*;

@Entity
public class FavoriteJockey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String jockeyName;

    public FavoriteJockey() {
    }

    public FavoriteJockey(String jockeyName) {
        this.jockeyName = jockeyName;
    }

    public Long getId() {
        return id;
    }

    public String getJockeyName() {
        return jockeyName;
    }

    public void setJockeyName(String jockeyName) {
        this.jockeyName = jockeyName;
    }
}
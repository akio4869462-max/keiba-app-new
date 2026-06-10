package org.example.keibaapp;

import jakarta.persistence.*;

@Entity
public class NotificationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String horseName;

    private String raceName;

    public NotificationHistory() {
    }

    public NotificationHistory(
            String horseName,
            String raceName) {

        this.horseName = horseName;
        this.raceName = raceName;
    }

    public Long getId() {
        return id;
    }

    public String getHorseName() {
        return horseName;
    }

    public String getRaceName() {
        return raceName;
    }
}
package org.example.keibaapp;

import jakarta.persistence.*;

@Entity
public class NotificationJockeyHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String jockeyName;

    private String raceName;

    public NotificationJockeyHistory() {
    }

    public NotificationJockeyHistory(
            String jockeyName,
            String raceName) {

        this.jockeyName = jockeyName;
        this.raceName = raceName;
    }

    public Long getId() {
        return id;
    }

    public String getJockeyName() {
        return jockeyName;
    }

    public String getRaceName() {
        return raceName;
    }
}
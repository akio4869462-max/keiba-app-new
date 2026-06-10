package org.example.keibaapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class KeibaAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(KeibaAppApplication.class, args);
    }

}

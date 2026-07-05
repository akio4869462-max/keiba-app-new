package org.example.keibaapp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface RaceResultRecordRepository
        extends JpaRepository<RaceResultRecord, Long> {

    List<RaceResultRecord> findByRaceDateGreaterThanEqual(LocalDate date);
}

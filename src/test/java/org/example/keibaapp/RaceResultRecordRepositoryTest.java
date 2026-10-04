package org.example.keibaapp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class RaceResultRecordRepositoryTest {

    @Autowired
    private RaceResultRecordRepository repository;

    private RaceResultRecord record(LocalDate date, String horseName, String modelVersion) {
        return new RaceResultRecord(
                date, "東京", 1, "テストレース", horseName,
                5.0, 1, 50, 1, 0.0, 1, modelVersion);
    }

    @Test
    void findByModelVersion_shouldReturnOnlyMatchingVersion() {
        repository.save(record(LocalDate.of(2026, 9, 20), "新A", "v2"));
        repository.save(record(LocalDate.of(2026, 9, 20), "新B", "v2"));
        repository.save(record(LocalDate.of(2026, 9, 13), "旧A", "v1"));

        List<RaceResultRecord> result = repository.findByModelVersion("v2");

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(r -> "v2".equals(r.getModelVersion())));
    }

    @Test
    void findByModelVersionIsNull_shouldReturnLegacyRecordsOnly() {
        // modelVersionを記録し始める前に保存された旧レコードはnullになる
        repository.save(record(LocalDate.of(2026, 7, 4), "旧馬", null));
        repository.save(record(LocalDate.of(2026, 9, 20), "新馬", "v2"));

        List<RaceResultRecord> result = repository.findByModelVersionIsNull();

        assertEquals(1, result.size());
        assertEquals("旧馬", result.get(0).getHorseName());
    }

    @Test
    void findByModelVersionAndRaceDateGreaterThanEqual_shouldFilterByBoth() {
        repository.save(record(LocalDate.of(2026, 9, 6), "古い", "v2"));
        repository.save(record(LocalDate.of(2026, 9, 20), "新しい", "v2"));
        repository.save(record(LocalDate.of(2026, 9, 20), "別版", "v1"));

        List<RaceResultRecord> result = repository
                .findByModelVersionAndRaceDateGreaterThanEqual("v2", LocalDate.of(2026, 9, 13));

        assertEquals(1, result.size());
        assertEquals("新しい", result.get(0).getHorseName());
    }

    @Test
    void findDistinctModelVersions_shouldReturnEachVersionOnceExcludingNull() {
        repository.save(record(LocalDate.of(2026, 9, 20), "a", "v2"));
        repository.save(record(LocalDate.of(2026, 9, 20), "b", "v2"));
        repository.save(record(LocalDate.of(2026, 9, 13), "c", "v1"));
        repository.save(record(LocalDate.of(2026, 7, 4), "d", null));

        List<String> versions = repository.findDistinctModelVersions();

        assertEquals(2, versions.size());
        assertTrue(versions.containsAll(List.of("v1", "v2")));
    }
}

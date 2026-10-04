package org.example.keibaapp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface RaceResultRecordRepository
        extends JpaRepository<RaceResultRecord, Long> {

    List<RaceResultRecord> findByRaceDateGreaterThanEqual(LocalDate date);

    List<RaceResultRecord> findByModelVersion(String modelVersion);

    // modelVersionを記録し始める前に保存された旧レコードはnullになる。
    // findByModelVersion(null)でも派生クエリはIS NULLになるが、意図が読み取れるよう明示的なメソッドにしている
    List<RaceResultRecord> findByModelVersionIsNull();

    List<RaceResultRecord> findByModelVersionAndRaceDateGreaterThanEqual(String modelVersion, LocalDate date);

    // 保存されているモデル版数の一覧(nullは含まない。旧レコードはfindByModelVersionIsNullで別途取得)
    @Query("select distinct r.modelVersion from RaceResultRecord r where r.modelVersion is not null")
    List<String> findDistinctModelVersions();
}

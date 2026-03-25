package com.urlshortener.u_r_l__service3.repository;

import com.urlshortener.u_r_l__service3.entity.AvailableUrl;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AvailableUrlRepository extends JpaRepository<AvailableUrl, String> {

    @Query(value = "SELECT code FROM available_url ORDER BY code LIMIT :batchSize", nativeQuery = true)
    List<String> fetchBatch(@Param("batchSize") int batchSize);

    @Modifying
    @Query(value = "DELETE FROM available_url WHERE code = :code", nativeQuery = true)
    int deleteCodeIfPresent(@Param("code") String code);

    @Modifying
    @Query(value = "DELETE FROM available_url WHERE code IN (:codes)", nativeQuery = true)
    int deleteCodesInBatch(@Param("codes") List<String> codes);
}

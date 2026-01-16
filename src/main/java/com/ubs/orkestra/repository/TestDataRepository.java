package com.ubs.orkestra.repository;

import com.ubs.orkestra.model.TestData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestDataRepository extends JpaRepository<TestData, Long> {

    List<TestData> findByDataIdIn(List<Long> dataIds);

    List<TestData> findByApplicationId(Long applicationId);

    Page<TestData> findByApplicationId(Long applicationId, Pageable pageable);

    void deleteByDataIdIn(List<Long> dataIds);

    boolean existsByDataId(Long dataId);

    @Query("SELECT td FROM TestData td WHERE " +
           "(:applicationName IS NULL OR LOWER(td.applicationName) LIKE LOWER(CONCAT('%', :applicationName, '%'))) AND " +
           "(:category IS NULL OR LOWER(td.category) LIKE LOWER(CONCAT('%', :category, '%'))) AND " +
           "(:description IS NULL OR LOWER(td.description) LIKE LOWER(CONCAT('%', :description, '%')))")
    List<TestData> searchByFilters(@Param("applicationName") String applicationName,
                                   @Param("category") String category,
                                   @Param("description") String description);

    @Query("SELECT td FROM TestData td WHERE " +
           "(:applicationName IS NULL OR LOWER(td.applicationName) LIKE LOWER(CONCAT('%', :applicationName, '%'))) AND " +
           "(:category IS NULL OR LOWER(td.category) LIKE LOWER(CONCAT('%', :category, '%'))) AND " +
           "(:description IS NULL OR LOWER(td.description) LIKE LOWER(CONCAT('%', :description, '%')))")
    Page<TestData> searchByFilters(@Param("applicationName") String applicationName,
                                   @Param("category") String category,
                                   @Param("description") String description,
                                   Pageable pageable);
}

package com.ubs.orkestra.repository;

import com.ubs.orkestra.model.Application;
import com.ubs.orkestra.model.FlowStep;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FlowStepRepository extends JpaRepository<FlowStep, Long> {

    List<FlowStep> findByApplication(Application application);

    Page<FlowStep> findByApplication(Application application, Pageable pageable);

    List<FlowStep> findByIdIn(List<Long> ids);

    @Query(value = "SELECT COUNT(*) > 0 FROM flow_steps WHERE test_data_ids LIKE CONCAT('%', :testDataId, '%')", nativeQuery = true)
    boolean existsByTestDataId(@Param("testDataId") String testDataId);
}

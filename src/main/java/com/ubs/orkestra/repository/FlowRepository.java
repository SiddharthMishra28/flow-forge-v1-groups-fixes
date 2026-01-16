package com.ubs.orkestra.repository;

import com.ubs.orkestra.model.Flow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FlowRepository extends JpaRepository<Flow, Long> {
    
    List<Flow> findBySquashTestCaseId(Long squashTestCaseId);
    
    Page<Flow> findBySquashTestCaseId(Long squashTestCaseId, Pageable pageable);
}
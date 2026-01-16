package com.ubs.orkestra.repository;

import com.ubs.orkestra.enums.ExecutionStatus;
import com.ubs.orkestra.model.FlowExecution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface FlowExecutionRepository extends JpaRepository<FlowExecution, UUID> {
    
    List<FlowExecution> findByFlowId(Long flowId);
    
    Page<FlowExecution> findByFlowId(Long flowId, Pageable pageable);
    
    Page<FlowExecution> findByFlowIdIn(List<Long> flowIds, Pageable pageable);
    
    List<FlowExecution> findByStatus(ExecutionStatus status);
    
    List<FlowExecution> findByFlowIdAndStatus(Long flowId, ExecutionStatus status);
    
    Long countByStatus(ExecutionStatus status);
    
    @Query("SELECT AVG(FUNCTION('TIMESTAMPDIFF', MINUTE, fe.startTime, fe.endTime)) FROM FlowExecution fe WHERE fe.endTime IS NOT NULL")
    List<Object[]> findAverageExecutionTime();
    
    @Query("SELECT FUNCTION('DATE', fe.createdAt), " +
           "SUM(CASE WHEN fe.status = 'PASSED' THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN fe.status = 'FAILED' THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN fe.status = 'CANCELLED' THEN 1 ELSE 0 END), " +
           "COUNT(*) " +
           "FROM FlowExecution fe WHERE fe.createdAt >= :startDate " +
           "GROUP BY FUNCTION('DATE', fe.createdAt) ORDER BY FUNCTION('DATE', fe.createdAt)")
    List<Object[]> findPassFailTrendData(@Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT FUNCTION('DATE', fe.createdAt), COUNT(*), " +
           "AVG(FUNCTION('TIMESTAMPDIFF', MINUTE, fe.startTime, fe.endTime)) " +
           "FROM FlowExecution fe WHERE fe.createdAt >= :startDate AND fe.endTime IS NOT NULL " +
           "GROUP BY FUNCTION('DATE', fe.createdAt) ORDER BY FUNCTION('DATE', fe.createdAt)")
    List<Object[]> findDurationTrendData(@Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT fe.flowId, COUNT(*), " +
           "SUM(CASE WHEN fe.status = 'PASSED' THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN fe.status = 'FAILED' THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN fe.status = 'CANCELLED' THEN 1 ELSE 0 END) " +
           "FROM FlowExecution fe GROUP BY fe.flowId")
    List<Object[]> findExecutionStatsByFlow();
    
    @Query("SELECT fe.flowId, " +
           "AVG(FUNCTION('TIMESTAMPDIFF', MINUTE, fe.startTime, fe.endTime)), " +
           "MIN(FUNCTION('TIMESTAMPDIFF', MINUTE, fe.startTime, fe.endTime)), " +
           "MAX(FUNCTION('TIMESTAMPDIFF', MINUTE, fe.startTime, fe.endTime)), " +
           "COUNT(*) " +
           "FROM FlowExecution fe WHERE fe.endTime IS NOT NULL " +
           "GROUP BY fe.flowId")
    List<Object[]> findDurationStatsByFlow();
    
    @Query("SELECT fe.flowId, " +
           "SUM(CASE WHEN fe.status = 'FAILED' THEN 1 ELSE 0 END), " +
           "COUNT(*), MAX(fe.endTime) " +
           "FROM FlowExecution fe " +
           "GROUP BY fe.flowId " +
           "HAVING SUM(CASE WHEN fe.status = 'FAILED' THEN 1 ELSE 0 END) > 0 " +
           "ORDER BY SUM(CASE WHEN fe.status = 'FAILED' THEN 1 ELSE 0 END) DESC")
    List<Object[]> findTopFailingFlows(@Param("limit") int limit);
    
    @Query("SELECT 1L, 'All Applications', " +
           "COUNT(*), " +
           "SUM(CASE WHEN fe.status = 'PASSED' THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN fe.status = 'FAILED' THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN fe.status = 'CANCELLED' THEN 1 ELSE 0 END) " +
           "FROM FlowExecution fe")
    List<Object[]> findExecutionStatsByApplication();
    
    @Query("SELECT 1L, 'All Applications', " +
           "SUM(CASE WHEN fe.status = 'FAILED' THEN 1 ELSE 0 END), " +
           "COUNT(*), MAX(fe.endTime) " +
           "FROM FlowExecution fe")
    List<Object[]> findTopFailingApplications(@Param("limit") int limit);

    // Search across execution id (UUID as string), squashTestCaseId, squashTestCase
    @Query("SELECT fe FROM FlowExecution fe JOIN Flow f ON fe.flowId = f.id " +
           "WHERE (LOWER(CAST(fe.id as string)) LIKE LOWER(CONCAT('%', :term, '%')) " +
           "OR CAST(f.squashTestCaseId as string) LIKE CONCAT('%', :term, '%') " +
           "OR LOWER(f.squashTestCase) LIKE LOWER(CONCAT('%', :term, '%')))" )
    Page<FlowExecution> searchAll(@Param("term") String term, Pageable pageable);

    @Query("SELECT fe FROM FlowExecution fe JOIN Flow f ON fe.flowId = f.id " +
           "WHERE fe.flowId IN :flowIds AND (LOWER(CAST(fe.id as string)) LIKE LOWER(CONCAT('%', :term, '%')) " +
           "OR CAST(f.squashTestCaseId as string) LIKE CONCAT('%', :term, '%') " +
           "OR LOWER(f.squashTestCase) LIKE LOWER(CONCAT('%', :term, '%')))" )
    Page<FlowExecution> searchByFlowIds(@Param("flowIds") List<Long> flowIds, @Param("term") String term, Pageable pageable);

    Page<FlowExecution> findAll(Specification<FlowExecution> spec, Pageable pageable);

}

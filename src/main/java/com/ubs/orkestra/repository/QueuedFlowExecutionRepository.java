package com.ubs.orkestra.repository;

import com.ubs.orkestra.model.QueuedFlowExecution;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QueuedFlowExecutionRepository extends JpaRepository<QueuedFlowExecution, Long> {
    
    /**
     * Find queued executions ordered by priority (desc) and creation time (asc)
     * Higher priority and older executions are processed first
     */
    List<QueuedFlowExecution> findAllByOrderByPriorityDescCreatedAtAsc(Pageable pageable);
    
    /**
     * Find a queued execution by flow execution ID
     */
    Optional<QueuedFlowExecution> findByFlowExecutionId(UUID flowExecutionId);
    
    /**
     * Delete a queued execution by flow execution ID
     */
    void deleteByFlowExecutionId(UUID flowExecutionId);
    
    /**
     * Count total queued executions
     */
    long count();
}

package com.ubs.orkestra.service;

import com.ubs.orkestra.model.QueuedFlowExecution;
import com.ubs.orkestra.repository.QueuedFlowExecutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service to manage database-backed queue for flow executions.
 * Ensures flows survive server restarts and are executed when thread pool capacity is available.
 */
@Service
public class FlowExecutionQueueService {

    private static final Logger logger = LoggerFactory.getLogger(FlowExecutionQueueService.class);

    @Autowired
    private QueuedFlowExecutionRepository queuedFlowExecutionRepository;

    @Autowired
    @Lazy
    private FlowExecutionService flowExecutionService;

    @Autowired(required = false)
    @Qualifier("flowExecutionTaskExecutor")
    private ThreadPoolTaskExecutor flowExecutionTaskExecutor;

    @org.springframework.beans.factory.annotation.Value("${scheduling.queue-processing.polling-interval:30000}")
    private long queuePollingIntervalMs;

    @org.springframework.beans.factory.annotation.Value("${scheduling.queue-processing.initial-delay:10000}")
    private long queuePollingInitialDelayMs;

    @org.springframework.beans.factory.annotation.Value("${scheduling.queue-processing.max-retry-count:3}")
    private int maxRetryCount;

    /**
     * Add a flow execution to the queue
     */
    @Transactional
    public QueuedFlowExecution enqueueFlowExecution(UUID flowExecutionId, Long flowId, Long flowGroupId, 
                                                     Integer iteration, Integer revolutions, String category) {
        QueuedFlowExecution queuedExecution = new QueuedFlowExecution(
            flowExecutionId, flowId, flowGroupId, iteration, revolutions, category);
        
        queuedExecution = queuedFlowExecutionRepository.save(queuedExecution);
        
        logger.info("Enqueued flow execution {} for flow {} - Total queued: {}", 
                   flowExecutionId, flowId, queuedFlowExecutionRepository.count());
        
        return queuedExecution;
    }

    /**
     * Remove a flow execution from the queue
     */
    @Transactional
    public void dequeueFlowExecution(UUID flowExecutionId) {
        queuedFlowExecutionRepository.deleteByFlowExecutionId(flowExecutionId);
        logger.debug("Dequeued flow execution {} - Remaining queued: {}", 
                    flowExecutionId, queuedFlowExecutionRepository.count());
    }

    /**
     * Get the number of available execution slots in the thread pool
     */
    private int getAvailableExecutionSlots() {
        if (flowExecutionTaskExecutor == null) {
            logger.warn("FlowExecutionTaskExecutor not available, returning default capacity");
            return 5; // default
        }

        int activeThreads = flowExecutionTaskExecutor.getActiveCount();
        int maxPoolSize = flowExecutionTaskExecutor.getMaxPoolSize();
        int queueSize = flowExecutionTaskExecutor.getThreadPoolExecutor().getQueue().size();
        int queueCapacity = flowExecutionTaskExecutor.getThreadPoolExecutor().getQueue().remainingCapacity();
        
        // Available slots = (max pool size - active threads) + remaining queue capacity
        int availableSlots = (maxPoolSize - activeThreads) + queueCapacity;
        
        logger.debug("Thread pool status - Active: {}, Max: {}, Queue: {}, Available slots: {}", 
                    activeThreads, maxPoolSize, queueSize, availableSlots);
        
        return Math.max(0, availableSlots);
    }

    /**
     * Process queued flow executions when thread pool capacity is available.
     * 
     * Polling interval is configurable via 'scheduling.queue-processing.polling-interval' (default: 30000ms)
     * Initial delay is configurable via 'scheduling.queue-processing.initial-delay' (default: 10000ms)
     * 
     * This method ONLY processes flows up to available thread pool capacity.
     * It does NOT attempt to execute all queued flows at once.
     */
    @Scheduled(fixedDelayString = "${scheduling.queue-processing.polling-interval:30000}", 
               initialDelayString = "${scheduling.queue-processing.initial-delay:10000}")
    @Transactional
    public void processQueuedExecutions() {
        try {
            int availableSlots = getAvailableExecutionSlots();
            
            if (availableSlots <= 0) {
                long queuedCount = queuedFlowExecutionRepository.count();
                if (queuedCount > 0) {
                    logger.debug("No available execution slots. {} flows queued.", queuedCount);
                }
                return;
            }

            // Fetch queued executions up to available capacity
            Pageable pageable = PageRequest.of(0, availableSlots);
            List<QueuedFlowExecution> queuedExecutions = queuedFlowExecutionRepository
                .findAllByOrderByPriorityDescCreatedAtAsc(pageable);

            if (queuedExecutions.isEmpty()) {
                return;
            }

            logger.info("Processing {} queued flow executions (available slots: {})", 
                       queuedExecutions.size(), availableSlots);

            for (QueuedFlowExecution queuedExecution : queuedExecutions) {
                try {
                    UUID flowExecutionId = queuedExecution.getFlowExecutionId();
                    
                    logger.info("Starting queued flow execution: {} for flow: {}", 
                               flowExecutionId, queuedExecution.getFlowId());

                    // Start async execution
                    flowExecutionService.executeFlowAsync(flowExecutionId);

                    // Remove from queue after successful submission
                    dequeueFlowExecution(flowExecutionId);
                    
                    logger.info("Successfully started queued flow execution: {}", flowExecutionId);
                    
                } catch (Exception e) {
                    logger.error("Failed to process queued flow execution {}: {}", 
                                queuedExecution.getFlowExecutionId(), e.getMessage(), e);
                    
                    // Increment retry count
                    queuedExecution.setRetryCount(queuedExecution.getRetryCount() + 1);
                    
                    // Remove from queue if retry count exceeds threshold
                    // NOTE: This retry is for QUEUE PROCESSING failures (e.g., thread pool submission errors),
                    // NOT for flow execution failures. Flow execution failures are handled by FlowExecutionService.
                    if (queuedExecution.getRetryCount() >= maxRetryCount) {
                        logger.error("Removing queued flow execution {} after {} failed queue processing attempts", 
                                    queuedExecution.getFlowExecutionId(), queuedExecution.getRetryCount());
                        dequeueFlowExecution(queuedExecution.getFlowExecutionId());
                    } else {
                        queuedFlowExecutionRepository.save(queuedExecution);
                        logger.warn("Queue processing retry {} of {} for flow execution {}", 
                                   queuedExecution.getRetryCount(), maxRetryCount, queuedExecution.getFlowExecutionId());
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("Error processing queued flow executions: {}", e.getMessage(), e);
        }
    }

    /**
     * On application startup, resume any queued flow executions from the database.
     * 
     * IMPORTANT: This does NOT resume all queued flows at once!
     * It calls processQueuedExecutions() which ONLY processes flows up to available
     * thread pool capacity. Remaining flows stay queued and are processed by the
     * scheduled polling task as capacity becomes available.
     */
    @EventListener(ContextRefreshedEvent.class)
    @Transactional
    public void resumeQueuedExecutionsOnStartup() {
        try {
            long queuedCount = queuedFlowExecutionRepository.count();
            
            if (queuedCount > 0) {
                logger.info("Found {} queued flow executions on startup. Processing up to available thread pool capacity...", 
                           queuedCount);
                logger.info("Remaining queued flows will be processed automatically by scheduled polling (interval: {}ms)", 
                           queuePollingIntervalMs);
                
                // Trigger immediate processing - only processes up to available capacity
                processQueuedExecutions();
            } else {
                logger.info("No queued flow executions found on startup.");
            }
        } catch (Exception e) {
            logger.error("Error resuming queued flow executions on startup: {}", e.getMessage(), e);
        }
    }

    /**
     * Get current queue status
     */
    public long getQueuedCount() {
        return queuedFlowExecutionRepository.count();
    }
}

package com.ubs.orkestra.service;

import com.ubs.orkestra.enums.ExecutionStatus;
import com.ubs.orkestra.model.FlowExecution;
import com.ubs.orkestra.model.QueuedFlowExecution;
import com.ubs.orkestra.repository.FlowExecutionRepository;
import com.ubs.orkestra.repository.QueuedFlowExecutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Manages the DB-backed queue for flow executions.
 *
 * Design contract
 * ---------------
 * - Every flow that cannot be immediately assigned a thread-pool thread is persisted
 *   in the {@code queued_flow_executions} table BEFORE the HTTP response is returned.
 * - On startup the DB queue is replayed, ensuring no flow is lost across restarts.
 * - RUNNING flows that were active at the time of a crash/restart but are NOT in the
 *   DB queue (they had already been dequeued and were executing) are detected and
 *   re-submitted to {@link FlowExecutionService#executeFlowAsync} so execution resumes.
 * - {@link #processQueuedExecutions()} is the single scheduler that drains the DB queue
 *   as thread-pool capacity becomes available.
 */
@Service
public class FlowExecutionQueueService {

    private static final Logger logger = LoggerFactory.getLogger(FlowExecutionQueueService.class);

    @Autowired
    private QueuedFlowExecutionRepository queuedFlowExecutionRepository;

    @Autowired
    private FlowExecutionRepository flowExecutionRepository;

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

    // -------------------------------------------------------------------------
    // Public queue API
    // -------------------------------------------------------------------------

    /**
     * Persist a flow execution in the DB queue so it can survive a restart and be
     * picked up when a thread-pool slot becomes available.
     */
    @Transactional
    public QueuedFlowExecution enqueueFlowExecution(UUID flowExecutionId, Long flowId, Long flowGroupId,
                                                     Integer iteration, Integer revolutions, String category) {
        QueuedFlowExecution queuedExecution = new QueuedFlowExecution(
            flowExecutionId, flowId, flowGroupId, iteration, revolutions, category);

        queuedExecution = queuedFlowExecutionRepository.save(queuedExecution);

        logger.info("Enqueued flow execution {} for flow {} – total queued: {}",
                   flowExecutionId, flowId, queuedFlowExecutionRepository.count());

        return queuedExecution;
    }

    /**
     * Convenience overload: reads execution metadata from the existing FlowExecution
     * record and enqueues it.  Used when the thread pool rejects a task at submission
     * time (race condition between capacity check and actual submission).
     */
    @Transactional
    public void enqueueFromExistingExecution(UUID flowExecutionId) {
        FlowExecution fe = flowExecutionRepository.findById(flowExecutionId).orElse(null);
        if (fe == null) {
            logger.error("Cannot enqueue flow execution {} – record not found", flowExecutionId);
            return;
        }
        // Avoid double-queueing if it was already inserted
        if (queuedFlowExecutionRepository.findByFlowExecutionId(flowExecutionId).isPresent()) {
            logger.debug("Flow execution {} already in DB queue, skipping duplicate enqueue", flowExecutionId);
            return;
        }
        enqueueFlowExecution(
            fe.getId(),
            fe.getFlowId(),
            fe.getFlowGroup() != null ? fe.getFlowGroup().getId() : null,
            fe.getIteration(),
            fe.getRevolutions(),
            fe.getCategory()
        );
        logger.warn("Flow execution {} was rejected by the thread pool and re-routed to DB queue", flowExecutionId);
    }

    /**
     * Remove a flow execution from the queue after it has been successfully
     * submitted to the thread pool.
     */
    @Transactional
    public void dequeueFlowExecution(UUID flowExecutionId) {
        queuedFlowExecutionRepository.deleteByFlowExecutionId(flowExecutionId);
        logger.debug("Dequeued flow execution {} – remaining queued: {}",
                    flowExecutionId, queuedFlowExecutionRepository.count());
    }

    /** Current number of flows waiting in the DB queue. */
    public long getQueuedCount() {
        return queuedFlowExecutionRepository.count();
    }

    // -------------------------------------------------------------------------
    // Scheduled queue drain
    // -------------------------------------------------------------------------

    /**
     * Periodically drains the DB queue by submitting flows to the thread pool as
     * slots become available.
     *
     * <p>Only submits as many flows as there are free thread-pool slots so the pool
     * is never overloaded.  Remaining queued flows stay in the DB and are processed
     * in the next tick.
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
                    logger.debug("No available execution slots. {} flows in DB queue.", queuedCount);
                }
                return;
            }

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

                    flowExecutionService.executeFlowAsync(flowExecutionId);

                    // Remove from queue only after successful thread-pool submission
                    dequeueFlowExecution(flowExecutionId);

                    logger.info("Successfully started queued flow execution: {}", flowExecutionId);

                } catch (Exception e) {
                    logger.error("Failed to process queued flow execution {}: {}",
                                queuedExecution.getFlowExecutionId(), e.getMessage(), e);

                    queuedExecution.setRetryCount(queuedExecution.getRetryCount() + 1);

                    if (queuedExecution.getRetryCount() >= maxRetryCount) {
                        logger.error("Removing queued flow execution {} after {} failed queue-processing attempts",
                                    queuedExecution.getFlowExecutionId(), queuedExecution.getRetryCount());
                        dequeueFlowExecution(queuedExecution.getFlowExecutionId());
                    } else {
                        queuedFlowExecutionRepository.save(queuedExecution);
                        logger.warn("Queue-processing retry {} of {} for flow execution {}",
                                   queuedExecution.getRetryCount(), maxRetryCount, queuedExecution.getFlowExecutionId());
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Error processing queued flow executions: {}", e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Startup recovery
    // -------------------------------------------------------------------------

    /**
     * Unified startup recovery – runs after {@link ApplicationReadyEvent} with
     * {@code @Order(2)} so the pipeline-polling recovery ({@code @Order(1)} in
     * {@link PipelineStatusPollingService}) always runs first.
     *
     * <p>Two categories of flows need recovery:
     * <ol>
     *   <li><b>Queued flows</b> (in {@code queued_flow_executions}): flows that were
     *       waiting for a free thread when the server went down.  Their first GitLab
     *       pipeline was already triggered; {@link FlowExecutionService#executeFlowAsync}
     *       picks up from whatever state the pipeline is in.</li>
     *   <li><b>Orphaned RUNNING flows</b> (NOT in {@code queued_flow_executions}): flows
     *       whose {@code executeFlowAsync} thread simply vanished on shutdown.  They are
     *       re-submitted here so execution resumes from the current pipeline state.</li>
     * </ol>
     *
     * <p>The two categories are mutually exclusive: a flow is either in the DB queue
     * (waiting to start executing) or it was already executing (and therefore removed
     * from the DB queue at dequeue time).
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(2)
    @Transactional
    public void recoverOnStartup() {
        try {
            logger.info("=== STARTUP RECOVERY: Flow Execution Queue ===");

            // Snapshot queued IDs BEFORE we drain the queue so we know which RUNNING
            // flows already have executeFlowAsync on the way.
            Set<UUID> queuedFlowIds = queuedFlowExecutionRepository.findAll()
                .stream()
                .map(QueuedFlowExecution::getFlowExecutionId)
                .collect(Collectors.toSet());

            // Step 1 – drain the DB queue (queued/waiting flows)
            if (!queuedFlowIds.isEmpty()) {
                logger.info("Startup recovery: {} flows in DB queue – processing up to available capacity",
                           queuedFlowIds.size());
                processQueuedExecutions();
                logger.info("Remaining queued flows (if any) will be picked up by the scheduled poller every {}ms",
                           queuePollingIntervalMs);
            } else {
                logger.info("Startup recovery: no queued flows in DB queue");
            }

            // Step 2 – find RUNNING flows that lost their executeFlowAsync thread on shutdown
            List<FlowExecution> runningFlows = flowExecutionRepository.findByStatus(ExecutionStatus.RUNNING);
            List<FlowExecution> orphaned = runningFlows.stream()
                .filter(fe -> !queuedFlowIds.contains(fe.getId()))
                .collect(Collectors.toList());

            if (orphaned.isEmpty()) {
                logger.info("Startup recovery: no orphaned RUNNING flow executions found");
            } else {
                logger.warn("Startup recovery: found {} RUNNING flow executions without an active thread – re-launching",
                           orphaned.size());

                int resumed = 0;
                int requeued = 0;
                for (FlowExecution fe : orphaned) {
                    try {
                        flowExecutionService.executeFlowAsync(fe.getId());
                        resumed++;
                        logger.info("Startup recovery: re-launched executeFlowAsync for flow execution {}",
                                   fe.getId());
                    } catch (Exception e) {
                        // Thread pool is full at startup (many queued flows being submitted) –
                        // put this flow in the DB queue so the scheduler picks it up.
                        logger.warn("Startup recovery: thread pool full for flow {}, adding to DB queue: {}",
                                   fe.getId(), e.getMessage());
                        enqueueFlowExecution(
                            fe.getId(),
                            fe.getFlowId(),
                            fe.getFlowGroup() != null ? fe.getFlowGroup().getId() : null,
                            fe.getIteration(),
                            fe.getRevolutions(),
                            fe.getCategory()
                        );
                        requeued++;
                    }
                }
                logger.info("Startup recovery: resumed={}, re-queued={} out of {} orphaned flows",
                           resumed, requeued, orphaned.size());
            }

            logger.info("=== STARTUP RECOVERY: Flow Execution Queue COMPLETE ===");

        } catch (Exception e) {
            logger.error("Error during startup flow-execution queue recovery: {}", e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the number of additional flows that can be submitted to the thread pool
     * RIGHT NOW without queueing them internally.
     *
     * <p>We deliberately use only {@code maxPoolSize - activeThreadCount} and ignore the
     * pool's internal queue capacity.  Flows that fit in the internal queue are NOT
     * persisted in the DB queue, meaning they would be lost on a server restart.
     * By routing all overflow to the DB queue we guarantee restart-survival.
     */
    private int getAvailableExecutionSlots() {
        if (flowExecutionTaskExecutor == null) {
            logger.warn("FlowExecutionTaskExecutor not available, returning default capacity");
            return 5;
        }

        int activeThreads = flowExecutionTaskExecutor.getActiveCount();
        int maxPoolSize   = flowExecutionTaskExecutor.getMaxPoolSize();
        int queueSize     = flowExecutionTaskExecutor.getThreadPoolExecutor().getQueue().size();

        // Only count actual free thread slots – do NOT include internal queue capacity.
        int availableSlots = Math.max(0, maxPoolSize - activeThreads);

        logger.debug("Thread pool status – active: {}, max: {}, internalQueue: {}, availableSlots: {}",
                    activeThreads, maxPoolSize, queueSize, availableSlots);

        return availableSlots;
    }
}

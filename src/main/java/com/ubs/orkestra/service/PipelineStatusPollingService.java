package com.ubs.orkestra.service;

import com.ubs.orkestra.config.GitLabConfig;
import com.ubs.orkestra.enums.ExecutionStatus;
import com.ubs.orkestra.model.Application;
import com.ubs.orkestra.model.FlowStep;
import com.ubs.orkestra.model.PipelineExecution;
import com.ubs.orkestra.repository.FlowStepRepository;
import com.ubs.orkestra.repository.PipelineExecutionRepository;
import com.ubs.orkestra.util.GitLabApiClient;
import com.ubs.orkestra.util.OutputEnvParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dedicated service for polling GitLab pipeline statuses.
 * This service uses a separate thread pool and optimized polling strategy
 * to ensure fast and reliable status updates without blocking flow execution.
 */
@Service
public class PipelineStatusPollingService {

    private static final Logger logger = LoggerFactory.getLogger(PipelineStatusPollingService.class);

    @Autowired
    private PipelineExecutionRepository pipelineExecutionRepository;

    @Autowired
    private FlowStepRepository flowStepRepository;

    @Autowired
    private GitLabApiClient gitLabApiClient;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private GitLabConfig gitLabConfig;

    @Autowired
    private OutputEnvParser outputEnvParser;

    @org.springframework.beans.factory.annotation.Value("${flow-execution.polling-interval:5000}")
    private long pollingIntervalMs;

    @org.springframework.beans.factory.annotation.Value("${flow-execution.recovery.enabled:true}")
    private boolean recoveryEnabled;

    // Track active pipeline polls to avoid duplicate polling
    private final Map<Long, PipelinePollingContext> activePipelinePolls = new ConcurrentHashMap<>();

    /**
     * Context for tracking a pipeline being polled
     */
    private static class PipelinePollingContext {
        final Long pipelineExecutionId;
        final Long pipelineId;
        final LocalDateTime startTime;
        int pollCount;

        PipelinePollingContext(Long pipelineExecutionId, Long pipelineId) {
            this.pipelineExecutionId = pipelineExecutionId;
            this.pipelineId = pipelineId;
            this.startTime = LocalDateTime.now();
            this.pollCount = 0;
        }
    }

    /**
     * Register a pipeline for active polling
     */
    public void registerPipelineForPolling(PipelineExecution pipelineExecution) {
        if (pipelineExecution.getPipelineId() == null) {
            logger.warn("Cannot register pipeline execution {} for polling - no GitLab pipeline ID", 
                       pipelineExecution.getId());
            return;
        }

        // Avoid duplicate registrations
        if (activePipelinePolls.containsKey(pipelineExecution.getId())) {
            logger.debug("Pipeline execution {} already registered for polling", pipelineExecution.getId());
            return;
        }

        PipelinePollingContext context = new PipelinePollingContext(
            pipelineExecution.getId(), 
            pipelineExecution.getPipelineId()
        );
        
        activePipelinePolls.put(pipelineExecution.getId(), context);
        
        logger.info("Registered pipeline {} (execution {}) for active polling. Total active: {}", 
                   pipelineExecution.getPipelineId(), pipelineExecution.getId(), activePipelinePolls.size());
    }

    /**
     * On application startup, recover in-flight pipeline executions from database.
     * This ensures that pipelines that were running when the service was restarted
     * are automatically resumed for polling.
     * 
     * CRITICAL: Without this, in-flight flows would be lost on restart!
     * 
     * Uses ApplicationReadyEvent to ensure this runs AFTER queue recovery in FlowExecutionQueueService
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(1) // Run first, before queue processing
    @Transactional
    public void recoverInFlightPipelinesOnStartup() {
        try {
            if (!recoveryEnabled) {
                logger.info("Pipeline startup recovery is disabled (flow-execution.recovery.enabled=false)");
                return;
            }
            
            logger.info("=== STARTUP RECOVERY: Checking for in-flight pipeline executions ===");
            
            // Find all pipeline executions in RUNNING state
            List<PipelineExecution> runningPipelines = pipelineExecutionRepository.findByStatus(ExecutionStatus.RUNNING);
            
            if (runningPipelines.isEmpty()) {
                logger.info("No in-flight pipeline executions found. Service restart was clean.");
                return;
            }
            
            logger.warn("Found {} in-flight pipeline executions that need recovery!", runningPipelines.size());
            
            int recovered = 0;
            int alreadyCompleted = 0;
            int failed = 0;
            
            for (PipelineExecution pipeline : runningPipelines) {
                try {
                    // Check if pipeline has GitLab pipeline ID
                    if (pipeline.getPipelineId() == null) {
                        logger.warn("Pipeline execution {} has no GitLab pipeline ID, marking as FAILED", 
                                   pipeline.getId());
                        pipeline.setStatus(ExecutionStatus.FAILED);
                        pipeline.setEndTime(LocalDateTime.now());
                        pipelineExecutionRepository.save(pipeline);
                        failed++;
                        continue;
                    }
                    
                    // Verify pipeline still exists in GitLab and check its actual status
                    FlowStep flowStep = flowStepRepository.findById(pipeline.getFlowStepId()).orElse(null);
                    if (flowStep == null) {
                        logger.warn("Flow step {} not found for pipeline execution {}, marking as FAILED", 
                                   pipeline.getFlowStepId(), pipeline.getId());
                        pipeline.setStatus(ExecutionStatus.FAILED);
                        pipeline.setEndTime(LocalDateTime.now());
                        pipelineExecutionRepository.save(pipeline);
                        failed++;
                        continue;
                    }
                    
                    Application application = flowStep.getApplication();
                    if (application == null) {
                        logger.warn("Application not found for pipeline execution {}, marking as FAILED", 
                                   pipeline.getId());
                        pipeline.setStatus(ExecutionStatus.FAILED);
                        pipeline.setEndTime(LocalDateTime.now());
                        pipelineExecutionRepository.save(pipeline);
                        failed++;
                        continue;
                    }
                    
                    // Check current status from GitLab (if not in mock mode)
                    if (!gitLabConfig.isMockMode()) {
                        try {
                            GitLabApiClient.GitLabPipelineResponse status = gitLabApiClient
                                .getPipelineStatus(
                                    gitLabConfig.getBaseUrl(),
                                    application.getGitlabProjectId(),
                                    pipeline.getPipelineId(),
                                    applicationService.getDecryptedPersonalAccessToken(application.getId())
                                )
                                .block();
                            
                            if (status != null && status.isCompleted()) {
                                // Pipeline already completed while service was down
                                logger.info("Pipeline {} already completed in GitLab with status: {}", 
                                           pipeline.getPipelineId(), status.getStatus());
                                
                                pipeline.setStatus(status.isSuccessful() ? ExecutionStatus.PASSED : ExecutionStatus.FAILED);
                                pipeline.setEndTime(LocalDateTime.now());
                                
                                // Download artifacts
                                downloadAndParseArtifacts(pipeline, application, flowStep);
                                
                                pipelineExecutionRepository.save(pipeline);
                                alreadyCompleted++;
                            } else {
                                // Pipeline still running, register for polling
                                logger.info("Pipeline {} still running in GitLab, registering for polling", 
                                           pipeline.getPipelineId());
                                registerPipelineForPolling(pipeline);
                                recovered++;
                            }
                        } catch (Exception e) {
                            // If we can't check GitLab status, assume it's still running and register for polling
                            logger.warn("Could not verify GitLab status for pipeline {}, registering for polling: {}", 
                                       pipeline.getPipelineId(), e.getMessage());
                            registerPipelineForPolling(pipeline);
                            recovered++;
                        }
                    } else {
                        // Mock mode - just register for polling
                        logger.info("Mock mode: Registering pipeline {} for polling", pipeline.getPipelineId());
                        registerPipelineForPolling(pipeline);
                        recovered++;
                    }
                    
                } catch (Exception e) {
                    logger.error("Error recovering pipeline execution {}: {}", 
                               pipeline.getId(), e.getMessage(), e);
                    failed++;
                }
            }
            
            logger.info("=== STARTUP RECOVERY COMPLETE ===");
            logger.info("Total in-flight pipelines found: {}", runningPipelines.size());
            logger.info("Recovered for polling: {}", recovered);
            logger.info("Already completed in GitLab: {}", alreadyCompleted);
            logger.info("Failed to recover: {}", failed);
            logger.info("Active pipelines being polled: {}", getActivePollCount());
            
        } catch (Exception e) {
            logger.error("Critical error during startup pipeline recovery: {}", e.getMessage(), e);
        }
    }

    /**
     * Scheduled task that polls all active pipelines for status updates
     * Runs at a configurable interval (default: 5 seconds)
     */
    @Scheduled(fixedDelayString = "${flow-execution.polling-interval:5000}")
    @Transactional
    public void pollActivePipelines() {
        if (activePipelinePolls.isEmpty()) {
            return;
        }

        logger.debug("Polling {} active pipelines for status updates", activePipelinePolls.size());

        // Process each active pipeline
        activePipelinePolls.values().forEach(context -> {
            try {
                context.pollCount++;
                checkPipelineStatus(context);
            } catch (Exception e) {
                logger.error("Error polling pipeline {} (execution {}): {}", 
                           context.pipelineId, context.pipelineExecutionId, e.getMessage(), e);
            }
        });
    }

    /**
     * Check status of a single pipeline
     */
    private void checkPipelineStatus(PipelinePollingContext context) {
        try {
            // Fetch latest pipeline execution from DB
            PipelineExecution pipelineExecution = pipelineExecutionRepository.findById(context.pipelineExecutionId)
                .orElse(null);

            if (pipelineExecution == null) {
                logger.warn("Pipeline execution {} not found, removing from active polls", context.pipelineExecutionId);
                activePipelinePolls.remove(context.pipelineExecutionId);
                return;
            }

            // If already completed, remove from active polls
            if (pipelineExecution.getStatus() == ExecutionStatus.PASSED || 
                pipelineExecution.getStatus() == ExecutionStatus.FAILED) {
                logger.debug("Pipeline {} (execution {}) already completed with status {}, removing from active polls",
                           context.pipelineId, context.pipelineExecutionId, pipelineExecution.getStatus());
                activePipelinePolls.remove(context.pipelineExecutionId);
                return;
            }

            // Skip if no pipeline ID (shouldn't happen, but defensive check)
            if (pipelineExecution.getPipelineId() == null) {
                logger.warn("Pipeline execution {} has no GitLab pipeline ID, removing from active polls", 
                           context.pipelineExecutionId);
                activePipelinePolls.remove(context.pipelineExecutionId);
                return;
            }

            // Get flow step and application info
            FlowStep flowStep = flowStepRepository.findById(pipelineExecution.getFlowStepId())
                .orElse(null);
            
            if (flowStep == null) {
                logger.error("Cannot poll pipeline {} - flow step {} not found", 
                           context.pipelineId, pipelineExecution.getFlowStepId());
                activePipelinePolls.remove(context.pipelineExecutionId);
                return;
            }

            Application application = flowStep.getApplication();
            if (application == null) {
                logger.error("Cannot poll pipeline {} - no application found for flow step {}", 
                           context.pipelineId, pipelineExecution.getFlowStepId());
                activePipelinePolls.remove(context.pipelineExecutionId);
                return;
            }

            // Check GitLab pipeline status
            if (!gitLabConfig.isMockMode()) {
                GitLabApiClient.GitLabPipelineResponse status = gitLabApiClient
                    .getPipelineStatus(
                        gitLabConfig.getBaseUrl(),
                        application.getGitlabProjectId(),
                        pipelineExecution.getPipelineId(),
                        applicationService.getDecryptedPersonalAccessToken(application.getId())
                    )
                    .block();

                if (status != null && status.isCompleted()) {
                    // Pipeline completed - update status
                    pipelineExecution.setStatus(status.isSuccessful() ? ExecutionStatus.PASSED : ExecutionStatus.FAILED);
                    pipelineExecution.setEndTime(LocalDateTime.now());

                    // Download artifacts inline (avoid circular dependency)
                    downloadAndParseArtifacts(pipelineExecution, application, flowStep);

                    pipelineExecutionRepository.save(pipelineExecution);

                    logger.info("Pipeline {} (execution {}) completed with status {} after {} polls", 
                               context.pipelineId, context.pipelineExecutionId, 
                               pipelineExecution.getStatus(), context.pollCount);

                    // Remove from active polls
                    activePipelinePolls.remove(context.pipelineExecutionId);

                    // Trigger flow continuation if needed
                    triggerFlowContinuation(pipelineExecution);
                }
            }

        } catch (Exception e) {
            logger.error("Error checking pipeline status for execution {}: {}", 
                       context.pipelineExecutionId, e.getMessage(), e);
        }
    }

    /**
     * Trigger flow execution continuation after a pipeline completes
     */
    private void triggerFlowContinuation(PipelineExecution completedPipeline) {
        try {
            // Check if this is part of a flow that needs to continue
            if (completedPipeline.getFlowExecutionId() != null) {
                logger.debug("Pipeline completion may trigger flow continuation for execution {}", 
                           completedPipeline.getFlowExecutionId());
                
                // The flow execution service will handle continuation logic
                // This is handled by the executeFlowAsync method which waits for pipeline completion
            }
        } catch (Exception e) {
            logger.error("Error triggering flow continuation: {}", e.getMessage(), e);
        }
    }

    /**
     * Wait synchronously for a pipeline to complete (used when flow needs immediate result)
     * This is more efficient than the old approach as it leverages the active polling system
     */
    public PipelineExecution waitForPipelineCompletion(PipelineExecution pipelineExecution, long timeoutMs) {
        logger.info("Waiting for pipeline {} (execution {}) to complete with timeout {}ms", 
                   pipelineExecution.getPipelineId(), pipelineExecution.getId(), timeoutMs);

        long startTime = System.currentTimeMillis();
        long elapsed = 0;

        // Register for polling if not already registered
        if (!activePipelinePolls.containsKey(pipelineExecution.getId())) {
            registerPipelineForPolling(pipelineExecution);
        }

        while (elapsed < timeoutMs) {
            try {
                // Refresh from database to get latest status
                PipelineExecution latest = pipelineExecutionRepository.findById(pipelineExecution.getId())
                    .orElse(pipelineExecution);

                if (latest.getStatus() == ExecutionStatus.PASSED || 
                    latest.getStatus() == ExecutionStatus.FAILED) {
                    logger.info("Pipeline {} completed with status {} after {}ms", 
                               latest.getPipelineId(), latest.getStatus(), elapsed);
                    return latest;
                }

                // Sleep for polling interval
                Thread.sleep(Math.min(pollingIntervalMs, timeoutMs - elapsed));
                elapsed = System.currentTimeMillis() - startTime;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Pipeline wait interrupted");
                break;
            }
        }

        logger.warn("Pipeline {} wait timed out after {}ms", pipelineExecution.getPipelineId(), elapsed);
        return pipelineExecution;
    }

    /**
     * Get count of actively polled pipelines
     */
    public int getActivePollCount() {
        return activePipelinePolls.size();
    }

    /**
     * Clear all active polls (for testing/maintenance)
     */
    public void clearActivePollsForTesting() {
        activePipelinePolls.clear();
        logger.info("Cleared all active pipeline polls");
    }

    /**
     * Download and parse artifacts from pipeline execution
     * Inline implementation to avoid circular dependency with FlowExecutionService
     */
    private void downloadAndParseArtifacts(PipelineExecution pipelineExecution, Application application, FlowStep step) {
        try {
            // Get pipeline jobs
            GitLabApiClient.GitLabJobsResponse[] jobs = gitLabApiClient
                    .getPipelineJobs(gitLabConfig.getBaseUrl(), application.getGitlabProjectId(),
                                   pipelineExecution.getPipelineId(), 
                                   applicationService.getDecryptedPersonalAccessToken(application.getId()))
                    .block();
            
            if (jobs != null && jobs.length > 0) {
                // Find job for the specified test stage
                GitLabApiClient.GitLabJobsResponse targetJob = null;
                for (GitLabApiClient.GitLabJobsResponse job : jobs) {
                    if (step.getTestStage().equals(job.getStage())) {
                        targetJob = job;
                    }
                }
                
                if (targetJob != null) {
                    logger.info("Found target job {} (status: {}) in stage {} for pipeline {}",
                               targetJob.getId(), targetJob.getStatus(), targetJob.getStage(), pipelineExecution.getPipelineId());

                    // Set job information
                    pipelineExecution.setJobId(targetJob.getId());
                    pipelineExecution.setJobUrl(targetJob.getWebUrl());

                    // Download output.env
                    String artifactContent = gitLabApiClient
                            .downloadJobArtifact(gitLabConfig.getBaseUrl(), application.getGitlabProjectId(),
                                               targetJob.getId(), 
                                               applicationService.getDecryptedPersonalAccessToken(application.getId()), 
                                               "target/output.env")
                            .block();

                    if (artifactContent != null && !artifactContent.trim().isEmpty()) {
                        Map<String, String> parsedVariables = outputEnvParser.parseOutputEnv(artifactContent);

                        // Merge configured test data with artifact data
                        Map<String, String> runtimeTestData = new HashMap<>();
                        if (pipelineExecution.getConfiguredTestData() != null) {
                            runtimeTestData.putAll(pipelineExecution.getConfiguredTestData());
                        }
                        runtimeTestData.putAll(parsedVariables);

                        pipelineExecution.setRuntimeTestData(runtimeTestData);
                        logger.info("Successfully downloaded and parsed artifacts from job {}: {} variables",
                                   targetJob.getId(), parsedVariables.size());
                    } else {
                        logger.info("No artifact content found in job {}, using configured data",
                                   targetJob.getId());
                        pipelineExecution.setRuntimeTestData(pipelineExecution.getConfiguredTestData());
                    }
                } else {
                    logger.info("No job found for stage '{}' in pipeline {}",
                               step.getTestStage(), pipelineExecution.getPipelineId());
                }
            } else {
                logger.info("No jobs found for pipeline {}", pipelineExecution.getPipelineId());
            }
        } catch (Exception e) {
            logger.info("No artifacts available for pipeline {} in stage '{}': {}", 
                       pipelineExecution.getPipelineId(), step.getTestStage(), e.getMessage());
        }
    }
}

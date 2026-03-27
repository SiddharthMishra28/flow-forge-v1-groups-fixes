package com.ubs.orkestra.service;

import com.ubs.orkestra.config.GitLabConfig;
import com.ubs.orkestra.dto.SyncStatusDto;
import com.ubs.orkestra.enums.ExecutionStatus;
import com.ubs.orkestra.model.Application;
import com.ubs.orkestra.model.FlowExecution;
import com.ubs.orkestra.model.FlowStep;
import com.ubs.orkestra.model.PipelineExecution;
import com.ubs.orkestra.repository.ApplicationRepository;
import com.ubs.orkestra.repository.FlowExecutionRepository;
import com.ubs.orkestra.repository.FlowStepRepository;
import com.ubs.orkestra.repository.PipelineExecutionRepository;
import com.ubs.orkestra.util.GitLabApiClient;
import com.ubs.orkestra.util.OutputEnvParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service for synchronizing flow execution data with GitLab.
 * Provides manual recovery and data sync capabilities.
 */
@Service
public class SyncService {

    private static final Logger logger = LoggerFactory.getLogger(SyncService.class);

    @Autowired
    private FlowExecutionRepository flowExecutionRepository;

    @Autowired
    private PipelineExecutionRepository pipelineExecutionRepository;

    @Autowired
    private FlowStepRepository flowStepRepository;

    @Autowired
    private GitLabApiClient gitLabApiClient;

    @Autowired
    private GitLabConfig gitLabConfig;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private OutputEnvParser outputEnvParser;

    @Autowired
    private PipelineStatusPollingService pipelineStatusPollingService;
    
    @Autowired
    private ApplicationRepository applicationRepository;

    private final AtomicBoolean syncInProgress = new AtomicBoolean(false);
    private volatile SyncStatusDto currentSyncStatus = null;

    /**
     * Synchronize flow execution data with GitLab (ASYNC - runs in background).
     * This will:
     * 1. Query flow executions (limited by 'limit' parameter if > 0)
     * 2. For each pipeline with a GitLab pipeline ID, query GitLab for current status
     * 3. Update database with latest status and artifacts
     * 4. Register still-running pipelines for active polling
     * 
     * @param syncOnlyRunning If true, only sync pipelines in RUNNING state. If false, sync all.
     * @param limit Maximum number of flow executions to sync (0 = all)
     * @return SyncStatusDto with initial status (use getCurrentSyncStatus for progress)
     */
    @Async("pipelinePollingTaskExecutor")
    public CompletableFuture<SyncStatusDto> syncAllFlowExecutionDataAsync(boolean syncOnlyRunning, int limit) {
        SyncStatusDto result = syncAllFlowExecutionData(syncOnlyRunning, limit);
        return CompletableFuture.completedFuture(result);
    }

    /**
     * Main sync operation (called by async method, no @Transactional here)
     * Each pipeline sync gets its own transaction to avoid session issues
     * 
     * @param syncOnlyRunning If true, only sync RUNNING pipelines
     * @param limit Maximum number of flow executions to sync (0 = all)
     */
    private SyncStatusDto syncAllFlowExecutionData(boolean syncOnlyRunning, int limit) {
        
        // Check if sync is already in progress
        if (!syncInProgress.compareAndSet(false, true)) {
            SyncStatusDto status = new SyncStatusDto();
            status.setInProgress(true);
            status.setMessage("Sync operation already in progress. Please wait for it to complete.");
            return currentSyncStatus != null ? currentSyncStatus : status;
        }

        SyncStatusDto syncStatus = new SyncStatusDto();
        syncStatus.setInProgress(true);
        syncStatus.setStartTime(LocalDateTime.now());
        currentSyncStatus = syncStatus;

        try {
            logger.info("=== STARTING DATA SYNC FROM GITLAB ===");
            logger.info("Sync mode: {}", syncOnlyRunning ? "RUNNING pipelines only" : "ALL pipelines");
            if (limit > 0) {
                logger.info("Limit: {} most recent flow executions", limit);
            } else {
                logger.info("Limit: ALL flow executions");
            }

            // Get flow executions (limited if specified)
            List<FlowExecution> allFlowExecutions = flowExecutionRepository.findAll();
            
            // Apply limit by taking most recent executions
            List<FlowExecution> flowExecutions;
            if (limit > 0 && allFlowExecutions.size() > limit) {
                // Sort by ID descending (most recent first) and take 'limit' items
                flowExecutions = allFlowExecutions.stream()
                    .sorted((a, b) -> b.getId().compareTo(a.getId()))
                    .limit(limit)
                    .toList();
                logger.info("Limited to {} most recent flow executions (out of {} total)", 
                           flowExecutions.size(), allFlowExecutions.size());
            } else {
                flowExecutions = allFlowExecutions;
                logger.info("Processing all {} flow executions", flowExecutions.size());
            }
            
            syncStatus.setTotalFlowExecutions((long) flowExecutions.size());

            long processedFlows = 0;
            long processedPipelines = 0;
            long syncedFlows = 0;
            long syncedPipelines = 0;
            long updatedPipelines = 0;
            long failedPipelines = 0;
            long skippedPipelines = 0;
            long orphanedPipelines = 0;    // Flow step deleted after execution
            long recoveredPipelines = 0;   // Successfully synced despite being orphaned

            // Process each flow execution
            for (FlowExecution flowExecution : flowExecutions) {
                processedFlows++;
                
                // Update progress
                int progress = (int) ((processedFlows * 100) / flowExecutions.size());
                syncStatus.setProgressPercentage(progress);
                syncStatus.setProcessedFlowExecutions(processedFlows);
                
                if (processedFlows % 100 == 0) {
                    logger.info("Progress: {}/{} flow executions processed ({}%)", 
                               processedFlows, flowExecutions.size(), progress);
                }
                try {
                    logger.debug("Processing flow execution ID: {}", flowExecution.getId());
                    
                    // Get all pipeline executions for this flow
                    List<PipelineExecution> pipelineExecutions = 
                        pipelineExecutionRepository.findByFlowExecutionId(flowExecution.getId());
                    
                    if (pipelineExecutions.isEmpty()) {
                        logger.debug("No pipeline executions found for flow execution ID: {}", flowExecution.getId());
                        continue;
                    }

                    boolean flowHadUpdates = false;

                    // Process each pipeline execution
                    for (PipelineExecution pipeline : pipelineExecutions) {
                        processedPipelines++;
                        syncStatus.setProcessedPipelineExecutions(processedPipelines);
                        
                        try {
                            // Skip if no GitLab pipeline ID
                            if (pipeline.getPipelineId() == null) {
                                logger.debug("Pipeline execution {} has no GitLab pipeline ID, skipping", 
                                           pipeline.getId());
                                skippedPipelines++;
                                continue;
                            }

                            // FIXED: Check if we should sync this pipeline
                            // When syncOnlyRunning=true: Only check RUNNING pipelines
                            // When syncOnlyRunning=false: Check ALL pipelines for discrepancies
                            boolean shouldSync = !syncOnlyRunning || 
                                                (pipeline.getStatus() == ExecutionStatus.RUNNING);
                            
                            if (!shouldSync) {
                                logger.debug("Pipeline execution {} already completed ({}), skipping (syncOnlyRunning=true)", 
                                           pipeline.getId(), pipeline.getStatus());
                                skippedPipelines++;
                                continue;
                            }
                            
                            syncedPipelines++;

                            // CRITICAL FIX: Sync each pipeline in its own transaction to avoid "no session" errors
                            // This ensures each pipeline has a fresh Hibernate session
                            SyncResult result = syncSinglePipeline(pipeline.getId());
                            
                            if (result.wasUpdated) {
                                updatedPipelines++;
                                flowHadUpdates = true;
                            }
                            if (result.failed) {
                                failedPipelines++;
                                if (result.errorMessage != null) {
                                    syncStatus.addError(result.errorMessage);
                                }
                            }
                            if (result.skipped) {
                                skippedPipelines++;
                                // Track orphaned pipelines separately
                                if (result.skipReason != null && result.skipReason.contains("flow was edited")) {
                                    orphanedPipelines++;
                                    syncStatus.addWarning("Pipeline " + pipeline.getPipelineId() + 
                                        " (execution " + pipeline.getId() + "): " + result.skipReason);
                                }
                            }
                            if (result.recovered) {
                                recoveredPipelines++;
                                syncStatus.addWarning("Successfully recovered orphaned pipeline " + 
                                    pipeline.getPipelineId() + " (execution " + pipeline.getId() + 
                                    ") via URL fallback - flow was edited after execution");
                            }
                            
                        } catch (Exception e) {
                            logger.error("Error syncing pipeline execution {}: {}", 
                                       pipeline.getId(), e.getMessage(), e);
                            syncStatus.addError("Pipeline execution " + pipeline.getId() + ": " + e.getMessage());
                            failedPipelines++;
                        }
                    }

                    if (flowHadUpdates) {
                        syncedFlows++;
                    }
                    
                } catch (Exception e) {
                    logger.error("Error processing flow execution {}: {}", 
                               flowExecution.getId(), e.getMessage(), e);
                    syncStatus.addError("Flow execution " + flowExecution.getId() + ": " + e.getMessage());
                }
            }

            // Set final statistics
            syncStatus.setTotalPipelineExecutions((long) pipelineExecutionRepository.findAll().size());
            syncStatus.setProcessedFlowExecutions(processedFlows);
            syncStatus.setProcessedPipelineExecutions(processedPipelines);
            syncStatus.setSyncedFlowExecutions(syncedFlows);
            syncStatus.setSyncedPipelineExecutions(syncedPipelines);
            syncStatus.setUpdatedPipelineExecutions(updatedPipelines);
            syncStatus.setFailedPipelineExecutions(failedPipelines);
            syncStatus.setSkippedPipelineExecutions(skippedPipelines);
            syncStatus.setOrphanedPipelineExecutions(orphanedPipelines);
            syncStatus.setRecoveredPipelineExecutions(recoveredPipelines);
            syncStatus.setProgressPercentage(100);
            syncStatus.setEndTime(LocalDateTime.now());
            syncStatus.setInProgress(false);
            
            logger.info("=== DATA SYNC COMPLETE ===");
            logger.info("Flow executions processed: {}/{}", syncedFlows, syncStatus.getTotalFlowExecutions());
            logger.info("Pipeline executions processed: {}", syncedPipelines);
            logger.info("Pipeline executions updated: {}", updatedPipelines);
            logger.info("Pipeline executions failed: {}", failedPipelines);
            logger.info("Pipeline executions skipped: {}", skippedPipelines);
            logger.info("Orphaned pipeline executions (flow edited): {}", orphanedPipelines);
            logger.info("Recovered orphaned pipelines via URL: {}", recoveredPipelines);
            logger.info("Active pipelines being polled: {}", pipelineStatusPollingService.getActivePollCount());

            syncStatus.setMessage(String.format(
                "Sync completed. Processed %d flow executions, updated %d pipelines, failed %d, skipped %d, orphaned %d, recovered %d",
                syncedFlows, updatedPipelines, failedPipelines, skippedPipelines, orphanedPipelines, recoveredPipelines
            ));

            return syncStatus;
            
        } catch (Exception e) {
            logger.error("Critical error during data sync: {}", e.getMessage(), e);
            syncStatus.setEndTime(LocalDateTime.now());
            syncStatus.setInProgress(false);
            syncStatus.addError("Critical error: " + e.getMessage());
            syncStatus.setMessage("Sync failed with critical error: " + e.getMessage());
            return syncStatus;
            
        } finally {
            syncInProgress.set(false);
            currentSyncStatus = null;
        }
    }

    /**
     * Get current sync status (if sync is in progress)
     */
    public SyncStatusDto getCurrentSyncStatus() {
        if (currentSyncStatus != null) {
            return currentSyncStatus;
        }
        
        SyncStatusDto status = new SyncStatusDto();
        status.setInProgress(false);
        status.setMessage("No sync operation in progress");
        return status;
    }

    /**
     * Result of syncing a single pipeline
     */
    private static class SyncResult {
        boolean wasUpdated = false;
        boolean failed = false;
        boolean skipped = false;
        boolean recovered = false;  // Successfully synced despite being orphaned
        String errorMessage = null;
        String skipReason = null;
    }

    /**
     * Extract Application from pipeline URL when flow step is deleted.
     * 
     * CORRECT GitLab URL format: https://gitlab.com/namespace/project/-/pipelines/pipeline-id
     * Example: https://gitlab.com/org_rzx/pipeline-agent/-/pipelines/2413248770
     * 
     * GitLab stores EITHER:
     * - Numeric project ID (e.g., "12345678")
     * - Project path (e.g., "org_rzx/pipeline-agent")
     * 
     * @param pipeline Pipeline execution with pipelineUrl
     * @return Application if found, null otherwise
     */
    private Application extractApplicationFromPipelineUrl(PipelineExecution pipeline) {
        try {
            String pipelineUrl = pipeline.getPipelineUrl();
            if (pipelineUrl == null || pipelineUrl.isEmpty()) {
                logger.debug("Pipeline execution {} has no pipeline URL for fallback", pipeline.getId());
                return null;
            }
            
            // Parse GitLab project path from URL
            // Format: https://gitlab.com/namespace/project/-/pipelines/pipeline-id
            // Extract: "namespace/project"
            String[] parts = pipelineUrl.split("/-/pipelines/");
            if (parts.length < 2) {
                logger.warn("Cannot parse pipeline URL format (expected '/-/pipelines/' separator): {}", pipelineUrl);
                return null;
            }
            
            String beforePipelines = parts[0]; // e.g., "https://gitlab.com/org_rzx/pipeline-agent"
            
            // Remove the base URL to get the project path
            // Handle both http:// and https://
            String projectPath;
            if (beforePipelines.contains("://")) {
                String[] urlParts = beforePipelines.split("://", 2);
                if (urlParts.length < 2) {
                    logger.warn("Cannot parse URL protocol: {}", pipelineUrl);
                    return null;
                }
                
                String afterProtocol = urlParts[1]; // e.g., "gitlab.com/org_rzx/pipeline-agent"
                
                // Remove the domain (first segment after protocol)
                String[] pathParts = afterProtocol.split("/", 2);
                if (pathParts.length < 2) {
                    logger.warn("Cannot extract project path from URL: {}", pipelineUrl);
                    return null;
                }
                
                projectPath = pathParts[1]; // e.g., "org_rzx/pipeline-agent"
            } else {
                logger.warn("URL does not contain protocol: {}", pipelineUrl);
                return null;
            }
            
            logger.debug("Extracted project path '{}' from pipeline URL: {}", projectPath, pipelineUrl);
            
            // Try to find application by the extracted project path
            // GitLab API accepts both numeric ID and URL-encoded path
            Application application = applicationRepository.findByGitlabProjectId(projectPath)
                .orElse(null);
            
            if (application == null) {
                // Try URL-encoded version (GitLab API format)
                String urlEncodedPath = projectPath.replace("/", "%2F");
                application = applicationRepository.findByGitlabProjectId(urlEncodedPath)
                    .orElse(null);
            }
            
            if (application == null) {
                logger.warn("No application found for GitLab project path '{}' (or URL-encoded '{}') extracted from pipeline URL", 
                          projectPath, projectPath.replace("/", "%2F"));
                logger.warn("This likely means the Application's gitlabProjectId field contains a numeric ID, not the project path");
                logger.warn("Pipeline URL: {}", pipelineUrl);
                return null;
            }
            
            logger.info("Found application {} (ID: {}) for GitLab project path '{}'", 
                      application.getApplicationName(), application.getId(), projectPath);
            
            return application;
            
        } catch (Exception e) {
            logger.error("Error extracting application from pipeline URL for pipeline execution {}: {}", 
                       pipeline.getId(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * Sync a single pipeline in its own transaction.
     * CRITICAL: This method has @Transactional with REQUIRES_NEW to ensure
     * each pipeline gets its own Hibernate session, avoiding LazyInitializationException.
     * 
     * HANDLES ORPHANED PIPELINES: When flows are edited after execution, flow steps may be deleted.
     * This method uses fallback mechanisms to still sync the pipeline using the GitLab pipeline URL.
     * 
     * @param pipelineExecutionId Pipeline execution ID to sync
     * @return SyncResult indicating what happened
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncResult syncSinglePipeline(Long pipelineExecutionId) {
        SyncResult result = new SyncResult();
        
        try {
            // Fetch pipeline with fresh session
            PipelineExecution pipeline = pipelineExecutionRepository.findById(pipelineExecutionId)
                .orElse(null);
            
            if (pipeline == null) {
                logger.warn("Pipeline execution {} not found", pipelineExecutionId);
                result.skipped = true;
                result.skipReason = "Pipeline execution not found";
                return result;
            }

            // CRITICAL: Get flow step - might be null if flow was edited/deleted
            FlowStep flowStep = flowStepRepository.findById(pipeline.getFlowStepId())
                .orElse(null);
            
            Application application = null;
            
            if (flowStep != null) {
                // Normal path: Flow step exists, get application from it
                application = applicationRepository.findById(flowStep.getApplication().getId())
                    .orElse(null);
            }
            
            // FALLBACK: If flow step deleted or application not found, try to recover from pipeline URL
            if (application == null) {
                logger.warn("Flow step {} not found for pipeline execution {} - attempting fallback via pipeline URL", 
                          pipeline.getFlowStepId(), pipeline.getId());
                
                application = extractApplicationFromPipelineUrl(pipeline);
                
                if (application == null) {
                    logger.warn("Cannot recover application for pipeline execution {} - flow step deleted and URL parsing failed. Skipping.", 
                              pipeline.getId());
                    result.skipped = true;
                    result.skipReason = "Flow step deleted, cannot recover application (flow was edited after execution)";
                    return result;
                }
                
                logger.info("Successfully recovered application {} for orphaned pipeline execution {} via URL parsing", 
                          application.getId(), pipeline.getId());
                
                // Mark as recovered - we'll still sync it despite orphaned flow step
                result.recovered = true;
                flowStep = null; // Set to null to avoid using deleted flow step for artifact download
            }

            // Skip if mock mode
            if (gitLabConfig.isMockMode()) {
                logger.debug("Mock mode: Skipping GitLab query for pipeline {}", 
                           pipeline.getPipelineId());
                result.skipped = true;
                return result;
            }

            // Query GitLab for current status
            ExecutionStatus previousStatus = pipeline.getStatus();
            
            try {
                String decryptedToken = applicationService.getDecryptedPersonalAccessToken(application.getId());
                
                GitLabApiClient.GitLabPipelineResponse status = gitLabApiClient
                    .getPipelineStatus(
                        gitLabConfig.getBaseUrl(),
                        application.getGitlabProjectId(),
                        pipeline.getPipelineId(),
                        decryptedToken
                    )
                    .block();

                if (status == null) {
                    logger.warn("Could not get status from GitLab for pipeline {}", 
                              pipeline.getPipelineId());
                    result.skipped = true;
                    return result;
                }

                // Update status if changed
                if (status.isCompleted()) {
                    ExecutionStatus newStatus = status.isSuccessful() ? 
                        ExecutionStatus.PASSED : ExecutionStatus.FAILED;
                    
                    if (pipeline.getStatus() != newStatus) {
                        pipeline.setStatus(newStatus);
                        result.wasUpdated = true;
                        
                        if (pipeline.getEndTime() == null) {
                            pipeline.setEndTime(LocalDateTime.now());
                        }

                        // Download artifacts for completed pipelines (only if flow step exists)
                        if (flowStep != null) {
                            downloadAndParseArtifacts(pipeline, application, flowStep);
                        } else {
                            logger.info("Skipping artifact download for orphaned pipeline {} - flow step deleted", 
                                      pipeline.getPipelineId());
                        }
                        
                        logger.info("Updated pipeline {} from {} to {}", 
                                  pipeline.getPipelineId(), previousStatus, newStatus);
                    }
                } else {
                    // Pipeline still running - register for polling
                    if (pipeline.getStatus() != ExecutionStatus.RUNNING) {
                        pipeline.setStatus(ExecutionStatus.RUNNING);
                        result.wasUpdated = true;
                    }
                    
                    // Register for active polling
                    pipelineStatusPollingService.registerPipelineForPolling(pipeline);
                    logger.info("Pipeline {} still running, registered for polling", 
                              pipeline.getPipelineId());
                }

                if (result.wasUpdated) {
                    pipelineExecutionRepository.save(pipeline);
                }
                
            } catch (Exception e) {
                logger.error("Error querying GitLab for pipeline {}: {}", 
                           pipeline.getPipelineId(), e.getMessage());
                result.failed = true;
                result.errorMessage = "Pipeline " + pipeline.getPipelineId() + ": " + e.getMessage();
            }
            
        } catch (Exception e) {
            logger.error("Error syncing pipeline execution {}: {}", 
                       pipelineExecutionId, e.getMessage(), e);
            result.failed = true;
            result.errorMessage = "Pipeline execution " + pipelineExecutionId + ": " + e.getMessage();
        }
        
        return result;
    }

    /**
     * Download and parse artifacts from a pipeline execution
     */
    private void downloadAndParseArtifacts(PipelineExecution pipelineExecution, 
                                          Application application, FlowStep step) {
        try {
            GitLabApiClient.GitLabJobsResponse[] jobs = gitLabApiClient
                .getPipelineJobs(gitLabConfig.getBaseUrl(), application.getGitlabProjectId(),
                               pipelineExecution.getPipelineId(), 
                               applicationService.getDecryptedPersonalAccessToken(application.getId()))
                .block();
            
            if (jobs != null && jobs.length > 0) {
                GitLabApiClient.GitLabJobsResponse targetJob = null;
                for (GitLabApiClient.GitLabJobsResponse job : jobs) {
                    if (step.getTestStage().equals(job.getStage())) {
                        targetJob = job;
                        break;
                    }
                }
                
                if (targetJob != null) {
                    pipelineExecution.setJobId(targetJob.getId());
                    pipelineExecution.setJobUrl(targetJob.getWebUrl());

                    String artifactContent = gitLabApiClient
                        .downloadJobArtifact(gitLabConfig.getBaseUrl(), application.getGitlabProjectId(),
                                           targetJob.getId(), 
                                           applicationService.getDecryptedPersonalAccessToken(application.getId()), 
                                           "target/output.env")
                        .block();

                    if (artifactContent != null && !artifactContent.trim().isEmpty()) {
                        Map<String, String> parsedVariables = outputEnvParser.parseOutputEnv(artifactContent);
                        Map<String, String> runtimeTestData = new HashMap<>();
                        
                        if (pipelineExecution.getConfiguredTestData() != null) {
                            runtimeTestData.putAll(pipelineExecution.getConfiguredTestData());
                        }
                        runtimeTestData.putAll(parsedVariables);
                        pipelineExecution.setRuntimeTestData(runtimeTestData);
                        
                        logger.debug("Downloaded artifacts for pipeline {}: {} variables", 
                                   targetJob.getId(), parsedVariables.size());
                    } else {
                        pipelineExecution.setRuntimeTestData(pipelineExecution.getConfiguredTestData());
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Could not download artifacts for pipeline {}: {}", 
                       pipelineExecution.getPipelineId(), e.getMessage());
        }
    }
}

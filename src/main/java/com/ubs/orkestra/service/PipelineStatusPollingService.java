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
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for handling pipeline completion fallback scenarios.
 * 
 * <p><strong>Primary completion mechanism is WebHook-based (event-driven).</strong></p>
 * 
 * <p>This service provides fallback support for:</p>
 * <ul>
 *   <li>Startup recovery - checks if pipelines completed while service was down</li>
 *   <li>Mock mode support - simulates pipeline completion for testing</li>
 * </ul>
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

    @Autowired
    @Lazy
    private FlowExecutionService flowExecutionService;

    @org.springframework.beans.factory.annotation.Value("${flow-execution.recovery.enabled:true}")
    private boolean recoveryEnabled;

    /**
     * On application startup, check in-flight pipeline executions.
     * If a pipeline completed in GitLab while service was down, update status and trigger flow continuation.
     * If still running, mark as FAILED (webhook should have handled it).
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(1)
    @Transactional
    public void recoverInFlightPipelinesOnStartup() {
        try {
            if (!recoveryEnabled) {
                logger.info("Pipeline startup recovery is disabled (flow-execution.recovery.enabled=false)");
                return;
            }

            logger.info("=== STARTUP RECOVERY: Checking for in-flight pipeline executions ===");

            List<PipelineExecution> runningPipelines = pipelineExecutionRepository.findByStatus(ExecutionStatus.RUNNING);

            if (runningPipelines.isEmpty()) {
                logger.info("No in-flight pipeline executions found. Service restart was clean.");
                return;
            }

            logger.warn("Found {} in-flight pipeline executions that need recovery!", runningPipelines.size());

            int alreadyCompleted = 0;
            int failed = 0;

            for (PipelineExecution pipeline : runningPipelines) {
                try {
                    if (pipeline.getPipelineId() == null) {
                        logger.warn("Pipeline execution {} has no GitLab pipeline ID, marking as FAILED", pipeline.getId());
                        pipeline.setStatus(ExecutionStatus.FAILED);
                        pipeline.setEndTime(LocalDateTime.now());
                        pipelineExecutionRepository.save(pipeline);
                        failed++;
                        continue;
                    }

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
                        logger.warn("Application not found for pipeline execution {}, marking as FAILED", pipeline.getId());
                        pipeline.setStatus(ExecutionStatus.FAILED);
                        pipeline.setEndTime(LocalDateTime.now());
                        pipelineExecutionRepository.save(pipeline);
                        failed++;
                        continue;
                    }

                    if (!gitLabConfig.isMockMode()) {
                        GitLabApiClient.GitLabPipelineResponse status = gitLabApiClient
                            .getPipelineStatus(
                                gitLabConfig.getBaseUrl(),
                                application.getGitlabProjectId(),
                                pipeline.getPipelineId(),
                                applicationService.getDecryptedPersonalAccessToken(application.getId())
                            )
                            .block();

                        if (status != null && status.isCompleted()) {
                            logger.info("Pipeline {} already completed in GitLab with status: {}",
                                       pipeline.getPipelineId(), status.getStatus());

                            pipeline.setStatus(status.isSuccessful() ? ExecutionStatus.PASSED : ExecutionStatus.FAILED);
                            pipeline.setEndTime(LocalDateTime.now());
                            downloadAndParseArtifacts(pipeline, application, flowStep);
                            pipelineExecutionRepository.save(pipeline);
                            alreadyCompleted++;
                            triggerFlowContinuation(pipeline);
                        } else {
                            logger.warn("Pipeline {} still running in GitLab but webhook was not received. Marking as FAILED.",
                                       pipeline.getPipelineId());
                            pipeline.setStatus(ExecutionStatus.FAILED);
                            pipeline.setEndTime(LocalDateTime.now());
                            pipelineExecutionRepository.save(pipeline);
                            failed++;
                        }
                    } else {
                        logger.warn("Mock mode: Pipeline {} still running on startup. Marking as FAILED.", pipeline.getPipelineId());
                        pipeline.setStatus(ExecutionStatus.FAILED);
                        pipeline.setEndTime(LocalDateTime.now());
                        pipelineExecutionRepository.save(pipeline);
                        failed++;
                    }

                } catch (Exception e) {
                    logger.error("Error recovering pipeline execution {}: {}", pipeline.getId(), e.getMessage(), e);
                    failed++;
                }
            }

            logger.info("=== STARTUP RECOVERY COMPLETE ===");
            logger.info("Total in-flight pipelines found: {}", runningPipelines.size());
            logger.info("Already completed in GitLab: {}", alreadyCompleted);
            logger.info("Failed to recover (marked as FAILED): {}", failed);

        } catch (Exception e) {
            logger.error("Critical error during startup pipeline recovery: {}", e.getMessage(), e);
        }
    }

    /**
     * Called after pipeline completion to advance flow to next step.
     */
    void triggerFlowContinuation(PipelineExecution completedPipeline) {
        try {
            UUID flowExecutionId = completedPipeline.getFlowExecutionId();
            Long flowStepId = completedPipeline.getFlowStepId();
            if (flowExecutionId != null && flowStepId != null) {
                logger.debug("Triggering flow continuation: flowExecution={} completedStep={}",
                            flowExecutionId, flowStepId);
                flowExecutionService.advanceFlowToNextStep(flowExecutionId, flowStepId);
            }
        } catch (Exception e) {
            logger.error("Error triggering flow continuation for pipeline {}: {}",
                        completedPipeline.getId(), e.getMessage(), e);
        }
    }

    /**
     * Download and parse artifacts from pipeline execution.
     */
    private void downloadAndParseArtifacts(PipelineExecution pipelineExecution, Application application, FlowStep step) {
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
                    logger.info("Found target job {} (status: {}) in stage {} for pipeline {}",
                               targetJob.getId(), targetJob.getStatus(), targetJob.getStage(), pipelineExecution.getPipelineId());

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
                        logger.info("Successfully downloaded and parsed artifacts from job {}: {} variables",
                                   targetJob.getId(), parsedVariables.size());
                    } else {
                        logger.info("No artifact content found in job {}, using configured data", targetJob.getId());
                        pipelineExecution.setRuntimeTestData(pipelineExecution.getConfiguredTestData());
                    }
                } else {
                    logger.info("No job found for stage '{}' in pipeline {}",
                               step.getTestStage(), pipelineExecution.getPipelineId());
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to download artifacts for pipeline {}: {}",
                       pipelineExecution.getPipelineId(), e.getMessage());
            if (pipelineExecution.getRuntimeTestData() == null) {
                pipelineExecution.setRuntimeTestData(pipelineExecution.getConfiguredTestData());
            }
        }
    }
}

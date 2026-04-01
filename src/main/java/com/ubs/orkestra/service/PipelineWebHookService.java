package com.ubs.orkestra.service;

import com.ubs.orkestra.config.GitLabConfig;
import com.ubs.orkestra.dto.GitLabWebHookPayload;
import com.ubs.orkestra.enums.ExecutionStatus;
import com.ubs.orkestra.model.Application;
import com.ubs.orkestra.model.FlowStep;
import com.ubs.orkestra.model.PipelineExecution;
import com.ubs.orkestra.repository.ApplicationRepository;
import com.ubs.orkestra.repository.FlowStepRepository;
import com.ubs.orkestra.repository.PipelineExecutionRepository;
import com.ubs.orkestra.util.GitLabApiClient;
import com.ubs.orkestra.util.OutputEnvParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for handling GitLab Pipeline WebHook events.
 * Replaces the polling-based mechanism with event-driven pipeline completion handling.
 */
@Service
public class PipelineWebHookService {

    private static final Logger logger = LoggerFactory.getLogger(PipelineWebHookService.class);

    @Autowired
    private PipelineExecutionRepository pipelineExecutionRepository;

    @Autowired
    private FlowStepRepository flowStepRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private GitLabApiClient gitLabApiClient;

    @Autowired
    private OutputEnvParser outputEnvParser;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private GitLabConfig gitLabConfig;

    @Autowired
    @Lazy
    private FlowExecutionService flowExecutionService;

    /**
     * Process a GitLab pipeline webhook event.
     * This method is called when GitLab sends a webhook notification upon pipeline completion.
     *
     * @param payload The webhook payload from GitLab
     * @param secretToken Optional secret token for validation
     * @return Processing result with status and message
     */
    @Async("flowExecutionTaskExecutor")
    @Transactional
    public WebHookProcessingResult processWebHookEvent(GitLabWebHookPayload payload, String secretToken) {
        logger.info("Processing GitLab webhook event for pipeline: {}", 
                    payload.getObjectAttributes() != null ? payload.getObjectAttributes().getId() : "unknown");

        try {
            // Validate payload
            if (payload.getObjectAttributes() == null) {
                return new WebHookProcessingResult(false, "Invalid payload: missing object_attributes");
            }

            GitLabWebHookPayload.PipelineObjectAttributes attributes = payload.getObjectAttributes();
            Long pipelineId = attributes.getId();
            String status = attributes.getStatus();

            if (pipelineId == null) {
                return new WebHookProcessingResult(false, "Invalid payload: missing pipeline ID");
            }

            logger.info("Webhook received for pipeline ID: {}, status: {}", pipelineId, status);

            // Find the PipelineExecution by GitLab pipeline ID
            // Note: We need to search across all pipeline executions since pipeline_id is not unique indexed
            List<PipelineExecution> matchingExecutions = pipelineExecutionRepository.findByPipelineId(pipelineId);
            
            if (matchingExecutions.isEmpty()) {
                logger.warn("No PipelineExecution found for GitLab pipeline ID: {}", pipelineId);
                return new WebHookProcessingResult(false, "No matching PipelineExecution found for pipeline ID: " + pipelineId);
            }

            // In most cases, there should be only one matching execution
            PipelineExecution pipelineExecution = matchingExecutions.get(0);
            if (matchingExecutions.size() > 1) {
                logger.warn("Multiple PipelineExecution records found for pipeline ID: {}. Using the most recent one.", pipelineId);
                // Use the most recently created one
                pipelineExecution = matchingExecutions.get(matchingExecutions.size() - 1);
            }

            // Check if already processed
            if (pipelineExecution.getStatus() == ExecutionStatus.PASSED || 
                pipelineExecution.getStatus() == ExecutionStatus.FAILED) {
                logger.info("Pipeline execution {} already in terminal state {}, ignoring webhook", 
                           pipelineExecution.getId(), pipelineExecution.getStatus());
                return new WebHookProcessingResult(true, "Pipeline already processed with status: " + pipelineExecution.getStatus());
            }

            // Update pipeline status based on webhook
            ExecutionStatus newStatus = mapGitLabStatusToExecutionStatus(status);
            pipelineExecution.setStatus(newStatus);
            pipelineExecution.setEndTime(LocalDateTime.now());

            // Update pipeline URL if available
            if (attributes.getUrl() != null) {
                pipelineExecution.setPipelineUrl(attributes.getUrl());
            }

            // Extract variables from webhook payload
            Map<String, String> webhookVariables = extractVariablesFromWebHook(attributes);
            
            // Merge with configured test data
            Map<String, String> runtimeTestData = new HashMap<>();
            if (pipelineExecution.getConfiguredTestData() != null) {
                runtimeTestData.putAll(pipelineExecution.getConfiguredTestData());
            }
            
            // Try to download and parse artifacts for runtime variables
            Map<String, String> artifactVariables = downloadArtifactVariables(pipelineExecution);
            if (artifactVariables != null && !artifactVariables.isEmpty()) {
                runtimeTestData.putAll(artifactVariables);
            }
            
            // Also include variables from webhook (these may include runtime variables)
            if (!webhookVariables.isEmpty()) {
                runtimeTestData.putAll(webhookVariables);
            }
            
            pipelineExecution.setRuntimeTestData(runtimeTestData);
            pipelineExecutionRepository.save(pipelineExecution);

            logger.info("Updated PipelineExecution {} to status {} with {} runtime variables", 
                       pipelineExecution.getId(), newStatus, runtimeTestData.size());

            // Trigger flow continuation to advance to next step
            UUID flowExecutionId = pipelineExecution.getFlowExecutionId();
            Long flowStepId = pipelineExecution.getFlowStepId();
            
            if (flowExecutionId != null && flowStepId != null) {
                logger.info("Triggering flow continuation: flowExecution={} completedStep={}", 
                           flowExecutionId, flowStepId);
                flowExecutionService.advanceFlowToNextStep(flowExecutionId, flowStepId);
            } else {
                logger.error("Invalid pipeline execution: missing flowExecutionId or flowStepId");
                return new WebHookProcessingResult(false, "Invalid pipeline execution record");
            }

            return new WebHookProcessingResult(true, "Successfully processed webhook for pipeline: " + pipelineId);

        } catch (Exception e) {
            logger.error("Error processing webhook event: {}", e.getMessage(), e);
            return new WebHookProcessingResult(false, "Error processing webhook: " + e.getMessage());
        }
    }

    /**
     * Map GitLab pipeline status to internal ExecutionStatus
     */
    private ExecutionStatus mapGitLabStatusToExecutionStatus(String gitLabStatus) {
        if (gitLabStatus == null) {
            return ExecutionStatus.FAILED;
        }
        
        switch (gitLabStatus.toLowerCase()) {
            case "success":
            case "passed":
                return ExecutionStatus.PASSED;
            case "failed":
            case "failure":
            case "cancelled":
            case "canceled":
            case "skipped":
                return ExecutionStatus.FAILED;
            case "running":
                return ExecutionStatus.RUNNING;
            case "pending":
            case "waiting_for_resource":
            case "preparing":
            case "scheduled":
                return ExecutionStatus.SCHEDULED;
            default:
                logger.warn("Unknown GitLab status: {}, defaulting to FAILED", gitLabStatus);
                return ExecutionStatus.FAILED;
        }
    }

    /**
     * Extract variables from webhook payload
     */
    private Map<String, String> extractVariablesFromWebHook(GitLabWebHookPayload.PipelineObjectAttributes attributes) {
        Map<String, String> variables = new HashMap<>();
        
        if (attributes.getVariables() != null) {
            for (GitLabWebHookPayload.PipelineObjectAttributes.Variable var : attributes.getVariables()) {
                if (var.getKey() != null && var.getValue() != null) {
                    variables.put(var.getKey(), var.getValue());
                    logger.debug("Extracted variable from webhook: {}={}", var.getKey(), var.getValue());
                }
            }
        }
        
        return variables;
    }

    /**
     * Download and parse artifact variables from GitLab
     */
    private Map<String, String> downloadArtifactVariables(PipelineExecution pipelineExecution) {
        try {
            FlowStep flowStep = flowStepRepository.findById(pipelineExecution.getFlowStepId()).orElse(null);
            if (flowStep == null) {
                logger.warn("Flow step {} not found for pipeline execution {}", 
                           pipelineExecution.getFlowStepId(), pipelineExecution.getId());
                return null;
            }

            Application application = flowStep.getApplication();
            if (application == null) {
                logger.warn("Application not found for flow step {}", pipelineExecution.getFlowStepId());
                return null;
            }

            if (pipelineExecution.getPipelineId() == null) {
                return null;
            }

            // Get pipeline jobs
            GitLabApiClient.GitLabJobsResponse[] jobs = gitLabApiClient
                    .getPipelineJobs(
                        gitLabConfig.getBaseUrl(), 
                        application.getGitlabProjectId(),
                        pipelineExecution.getPipelineId(),
                        applicationService.getDecryptedPersonalAccessToken(application.getId())
                    )
                    .block();

            if (jobs != null && jobs.length > 0) {
                // Find job for the specified test stage
                GitLabApiClient.GitLabJobsResponse targetJob = null;
                for (GitLabApiClient.GitLabJobsResponse job : jobs) {
                    if (flowStep.getTestStage().equals(job.getStage())) {
                        targetJob = job;
                        break;
                    }
                }

                if (targetJob != null) {
                    logger.info("Found target job {} (status: {}) in stage {} for pipeline {}",
                               targetJob.getId(), targetJob.getStatus(), targetJob.getStage(), 
                               pipelineExecution.getPipelineId());

                    // Set job information
                    pipelineExecution.setJobId(targetJob.getId());
                    pipelineExecution.setJobUrl(targetJob.getWebUrl());

                    // Download output.env
                    String artifactContent = gitLabApiClient
                            .downloadJobArtifact(
                                gitLabConfig.getBaseUrl(), 
                                application.getGitlabProjectId(),
                                targetJob.getId(),
                                applicationService.getDecryptedPersonalAccessToken(application.getId()),
                                "target/output.env"
                            )
                            .block();

                    if (artifactContent != null && !artifactContent.trim().isEmpty()) {
                        Map<String, String> parsedVariables = outputEnvParser.parseOutputEnv(artifactContent);
                        logger.info("Successfully downloaded and parsed artifacts from job {}: {} variables",
                                   targetJob.getId(), parsedVariables.size());
                        return parsedVariables;
                    } else {
                        logger.info("No artifact content found in job {}", targetJob.getId());
                    }
                }
            }
        } catch (Exception e) {
            logger.info("No artifacts available for pipeline {}: {}", 
                       pipelineExecution.getPipelineId(), e.getMessage());
        }
        
        return null;
    }

    /**
     * Validate secret token for webhook security
     */
    public boolean validateSecretToken(String receivedToken, String expectedToken) {
        if (expectedToken == null || expectedToken.trim().isEmpty()) {
            // If no secret is configured, accept all requests (not recommended for production)
            logger.warn("No webhook secret token configured. Webhook endpoint is open.");
            return true;
        }
        
        if (receivedToken == null) {
            return false;
        }
        
        return expectedToken.equals(receivedToken);
    }

    /**
     * Result of webhook processing
     */
    public static class WebHookProcessingResult {
        private final boolean success;
        private final String message;

        public WebHookProcessingResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }
}

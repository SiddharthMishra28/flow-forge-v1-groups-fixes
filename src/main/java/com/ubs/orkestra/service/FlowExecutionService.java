package com.ubs.orkestra.service;

import com.ubs.orkestra.dto.*;
import com.ubs.orkestra.enums.ExecutionStatus;
import com.ubs.orkestra.model.*;
import com.ubs.orkestra.repository.*;
import com.ubs.orkestra.util.GitLabApiClient;
import com.ubs.orkestra.util.OutputEnvParser;
import com.ubs.orkestra.config.GitLabConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Transactional
public class FlowExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(FlowExecutionService.class);

    @Autowired
    private FlowExecutionRepository flowExecutionRepository;

    @Autowired
    private PipelineExecutionRepository pipelineExecutionRepository;

    @Autowired
    private PipelineExecutionTxService pipelineExecutionTxService;

    @Autowired
    private FlowRepository flowRepository;

    @Autowired
    private FlowStepRepository flowStepRepository;

    @Autowired
    private TestDataService testDataService;

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
    private SchedulingService schedulingService;

    @Autowired
    private FlowGroupRepository flowGroupRepository;

    @org.springframework.beans.factory.annotation.Value("${scheduling.pipeline-status.polling-interval:60000}")
    private long scheduledPollingIntervalMs;

    @org.springframework.beans.factory.annotation.Value("${flow-execution.polling-interval:15000}")
    private long flowExecutionPollingIntervalMs;

    @Autowired(required = false)
    @Qualifier("flowExecutionTaskExecutor")
    private ThreadPoolTaskExecutor flowExecutionTaskExecutor;

    public Map<String, Object> executeMultipleFlows(String flowIdsParam) {
        return executeMultipleFlows(flowIdsParam, null, null, null, null);
    }

    public Map<String, Object> executeMultipleFlows(String flowIdsParam, Long flowGroupId, Integer iteration, Integer revolutions, String category) {
        logger.info("Processing multiple flow execution request: {}", flowIdsParam);

        // Parse and validate flow IDs
        List<Long> flowIds = parseAndValidateFlowIds(flowIdsParam);

        // Get current thread pool status (handle test environment where executor might be null)
        int activeThreads = 0;
        int maxThreads = 20; // default
        int queueSize = 0;
        int availableCapacity = 20; // default

        if (flowExecutionTaskExecutor != null) {
            activeThreads = flowExecutionTaskExecutor.getActiveCount();
            maxThreads = flowExecutionTaskExecutor.getMaxPoolSize();
            queueSize = flowExecutionTaskExecutor.getThreadPoolExecutor().getQueue().size();
            availableCapacity = (maxThreads - activeThreads) +
                                (flowExecutionTaskExecutor.getThreadPoolExecutor().getQueue().remainingCapacity());
        } else {
            logger.warn("ThreadPoolTaskExecutor not available (likely test environment), using default capacity values");
        }

        logger.info("Thread pool status - Active: {}, Max: {}, Queue Size: {}, Available Capacity: {}",
                   activeThreads, maxThreads, queueSize, availableCapacity);

        // Create flow executions synchronously first, then start async execution
        List<FlowExecutionDto> acceptedExecutions = new ArrayList<>();
        List<Map<String, Object>> rejected = new ArrayList<>();

        for (int i = 0; i < flowIds.size(); i++) {
            Long flowId = flowIds.get(i);

            if (i < availableCapacity) {
                try {
                    // Create the flow execution record synchronously to get the UUID and details
                    // This only creates the database record, no GitLab interaction yet
                    FlowExecutionDto executionDto = createFlowExecution(flowId, flowGroupId, iteration, revolutions, category);
                    acceptedExecutions.add(executionDto);

                    logger.info("Flow {} accepted for execution with ID: {}", flowId, executionDto.getId());
                } catch (IllegalArgumentException e) {
                    logger.error("Flow {} rejected during creation: {}", flowId, e.getMessage());
                    Map<String, Object> rejectedFlow = new HashMap<>();
                    rejectedFlow.put("flowId", flowId);
                    rejectedFlow.put("status", "rejected");
                    rejectedFlow.put("reason", "flow_not_found");
                    rejectedFlow.put("message", e.getMessage());
                    rejected.add(rejectedFlow);
                }
            } else {
                Map<String, Object> rejectedFlow = new HashMap<>();
                rejectedFlow.put("flowId", flowId);
                rejectedFlow.put("status", "rejected");
                rejectedFlow.put("reason", "thread_pool_capacity");
                rejectedFlow.put("message", "Thread pool at capacity, flow execution rejected");
                rejected.add(rejectedFlow);

                logger.warn("Flow {} rejected due to thread pool capacity", flowId);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("summary", Map.of(
            "total_requested", flowIds.size(),
            "accepted", acceptedExecutions.size(),
            "rejected", rejected.size()
        ));
        result.put("accepted", acceptedExecutions);  // Now contains FlowExecutionDto objects
        result.put("rejected", rejected);
        result.put("thread_pool_status", Map.of(
            "active_threads", activeThreads,
            "max_threads", maxThreads,
            "queue_size", queueSize,
            "available_capacity", availableCapacity
        ));

        logger.info("Multiple flow execution request processed immediately - Accepted: {}, Rejected: {}", acceptedExecutions.size(), rejected.size());
        return result;
    }


    public Page<FlowExecutionDto> searchExecutionsByFlowIds(String flowIdsParam, String term, Pageable pageable) {
        logger.debug("Searching executions for multiple flows: {} with term '{}'", flowIdsParam, term);
        List<Long> flowIds = parseAndValidateFlowIds(flowIdsParam);
        Page<FlowExecution> page = flowExecutionRepository.searchByFlowIds(flowIds, term, pageable);
        List<FlowExecutionDto> dtos = page.getContent().stream().map(this::convertToDtoWithDetails).collect(Collectors.toList());
        return new PageImpl<>(dtos, pageable, page.getTotalElements());
    }

    public Page<FlowExecutionDto> getMultipleFlowExecutions(String flowIdsParam, Pageable pageable) {
        logger.debug("Fetching executions for multiple flows: {}", flowIdsParam);

        // Parse and validate flow IDs
        List<Long> flowIds = parseAndValidateFlowIds(flowIdsParam);

        // Get all executions for the specified flows
        Page<FlowExecution> executionsPage = flowExecutionRepository.findByFlowIdIn(flowIds, pageable);

        // Convert to DTOs with full details (flow, flowSteps, applications, pipelineExecutions)
        List<FlowExecutionDto> executionDtos = executionsPage.getContent().stream()
                .map(this::convertToDtoWithDetails)
                .collect(Collectors.toList());

        logger.debug("Found {} executions for flows: {}", executionDtos.size(), flowIds);

        return new PageImpl<>(executionDtos, pageable, executionsPage.getTotalElements());
    }

    public Page<FlowExecutionDto> searchAllFlowExecutions(String term, Pageable pageable) {
        logger.debug("Searching all flow executions with term: '{}' and pagination: {}", term, pageable);
        Page<FlowExecution> page = flowExecutionRepository.searchAll(term, pageable);
        List<FlowExecutionDto> dtos = page.getContent().stream().map(this::convertToDtoWithDetails).collect(Collectors.toList());
        return new PageImpl<>(dtos, pageable, page.getTotalElements());
    }

    public Page<FlowExecutionDto> searchFlowExecutionsAdvanced(UUID executionId, Long flowId, String flowGroupName, Long flowGroupId, Integer iteration, LocalDateTime fromDate, LocalDateTime toDate, Pageable pageable) {
        logger.debug("Advanced search flow executions with filters: executionId={}, flowId={}, flowGroupName={}, flowGroupId={}, iteration={}, fromDate={}, toDate={}", executionId, flowId, flowGroupName, flowGroupId, iteration, fromDate, toDate);

        // Use JPA Specification for efficient database-level filtering
        Page<FlowExecution> flowExecutionsPage = flowExecutionRepository.findAll(
            FlowExecutionSpecification.withFilters(executionId, flowId, flowGroupId, flowGroupName, iteration, fromDate, toDate),
            pageable);

        // Convert to DTOs with details
        List<FlowExecutionDto> dtos = flowExecutionsPage.getContent().stream()
                .map(this::convertToDtoWithDetails)
                .collect(Collectors.toList());

        return new PageImpl<>(dtos, pageable, flowExecutionsPage.getTotalElements());
    }

    public Page<FlowExecutionDto> getAllFlowExecutions(Pageable pageable) {
        logger.debug("Fetching all flow executions with pagination: {}", pageable);

        // Get all executions
        Page<FlowExecution> executionsPage = flowExecutionRepository.findAll(pageable);

        // Convert to DTOs with full details (flow, flowSteps, applications, pipelineExecutions)
        List<FlowExecutionDto> executionDtos = executionsPage.getContent().stream()
                .map(this::convertToDtoWithDetails)
                .collect(Collectors.toList());

        logger.debug("Found {} total executions", executionDtos.size());

        return new PageImpl<>(executionDtos, pageable, executionsPage.getTotalElements());
    }

    private List<Long> parseAndValidateFlowIds(String flowIdsParam) {
        if (flowIdsParam == null || flowIdsParam.trim().isEmpty()) {
            throw new IllegalArgumentException("Flow IDs parameter cannot be empty");
        }

        List<Long> flowIds = new ArrayList<>();
        String[] idStrings = flowIdsParam.split(",");

        for (String idString : idStrings) {
            try {
                Long flowId = Long.parseLong(idString.trim());
                if (flowId <= 0) {
                    throw new IllegalArgumentException("Flow ID must be a positive number: " + flowId);
                }
                flowIds.add(flowId);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid flow ID format: " + idString.trim());
            }
        }

        if (flowIds.isEmpty()) {
            throw new IllegalArgumentException("No valid flow IDs provided");
        }

        // Remove duplicates while preserving order
        List<Long> uniqueFlowIds = flowIds.stream().distinct().collect(Collectors.toList());

        if (uniqueFlowIds.size() != flowIds.size()) {
            logger.info("Removed {} duplicate flow IDs from request", flowIds.size() - uniqueFlowIds.size());
        }

        return uniqueFlowIds;
    }

    public FlowExecutionDto createFlowExecution(Long flowId) {
        return createFlowExecution(flowId, null, null, null, null);
    }

    public FlowExecutionDto createFlowExecution(Long flowId, Long flowGroupId, Integer iteration, Integer revolutions, String category) {
        logger.info("Creating flow execution for flow ID: {}", flowId);

        Flow flow = flowRepository.findById(flowId)
                .orElseThrow(() -> new IllegalArgumentException("Flow not found with ID: " + flowId));

        // Create flow execution record
        FlowExecution flowExecution = new FlowExecution(flowId, new HashMap<>());
        if (flowGroupId != null) {
            FlowGroup flowGroup = flowGroupRepository.findById(flowGroupId).orElseThrow(()-> new IllegalArgumentException("FlowGroup not found with ID: " + flowGroupId));
            flowExecution.setFlowGroup(flowGroup);
        }
        if (iteration != null) {
            flowExecution.setIteration(iteration);
        }
        if (revolutions != null) {
            flowExecution.setRevolutions(revolutions);
        }
        if (category != null) {
            flowExecution.setCategory(category);
        }
        flowExecution = flowExecutionRepository.save(flowExecution);

        // Pre-create placeholder PipelineExecution records for immediate visibility
        List<Long> stepIds = flow.getFlowStepIds();
        List<PipelineExecution> pipelineExecutions = new ArrayList<>();

        for (int i = 0; i < stepIds.size(); i++) {
            Long stepId = stepIds.get(i);
            FlowStep step = flowStepRepository.findById(stepId)
                    .orElseThrow(() -> new IllegalArgumentException("Flow step not found with ID: " + stepId));

            PipelineExecution placeholder = new PipelineExecution();
            placeholder.setFlowId(flowId);
            placeholder.setFlowExecutionId(flowExecution.getId());
            placeholder.setFlowStepId(stepId);
            // Pre-populate configured test data so clients can see inputs early
            placeholder.setConfiguredTestData(testDataService.mergeTestDataByIds(step.getTestDataIds()));
            placeholder.setRuntimeTestData(null);

            if (i == 0) {
                // Trigger the first pipeline asynchronously (return immediately after getting pipelineId/pipelineUrl)
                logger.info("Triggering first pipeline asynchronously for step: {}", stepId);
                PipelineExecution firstPipeline = triggerPipelineAsynchronously(flowExecution, step, placeholder);
                pipelineExecutions.add(firstPipeline);
            } else {
                // Create placeholder for subsequent steps
                placeholder.setStatus(ExecutionStatus.SCHEDULED);
                placeholder.setStartTime(null);
                pipelineExecutionTxService.saveNew(placeholder);
                pipelineExecutions.add(placeholder);
            }
        }

        logger.info("Created flow execution with ID: {} and triggered first pipeline asynchronously", flowExecution.getId());
        return convertToDtoWithDetails(flowExecution);
    }

    /**
     * Trigger the first pipeline and return immediately once we have pipelineId/pipelineUrl
     * Completion polling happens asynchronously
     */
    private PipelineExecution triggerPipelineAsynchronously(FlowExecution flowExecution, FlowStep step, PipelineExecution pipelineExecution) {
        try {
            Application application = step.getApplication();

            // Prepare variables for the first pipeline
            Map<String, String> pipelineVariables = new HashMap<>();
            Map<String, String> stepTestData = testDataService.mergeTestDataByIds(step.getTestDataIds());
            pipelineVariables.putAll(stepTestData);

            // Add the testTag from FlowStep to make it available in GitLab pipeline scope
            if (step.getTestTag() != null && !step.getTestTag().trim().isEmpty()) {
                pipelineVariables.put("testTag", step.getTestTag());
                logger.debug("Added testTag '{}' to pipeline variables for first step {}", step.getTestTag(), step.getId());
            }

            // Add flowExecutionUuid and applicationName to make them available in GitLab pipeline scope
            pipelineVariables.put("EXECUTION_UUID", flowExecution.getId().toString());
            pipelineVariables.put("APP_NAME", application.getApplicationName());
            logger.debug("Added EXECUTION_UUID '{}' and APP_NAME '{}' to pipeline variables for first step {}",
                        flowExecution.getId(), application.getApplicationName(), step.getId());

            // Save the pipeline execution record first
            pipelineExecution.setStatus(ExecutionStatus.RUNNING);
            pipelineExecution.setStartTime(LocalDateTime.now());
            pipelineExecution = pipelineExecutionRepository.save(pipelineExecution);

            if (gitLabConfig.isMockMode()) {
                // Mock mode for testing
                logger.info("MOCK MODE: Simulating first GitLab pipeline execution for project {} on branch {}",
                           application.getGitlabProjectId(), step.getBranch());

                // Simulate pipeline response
                long mockPipelineId = System.currentTimeMillis();
                String mockPipelineUrl = String.format("https://gitlab.com/%s/-/pipelines/%d",
                                                      application.getGitlabProjectId(), mockPipelineId);

                pipelineExecution.setPipelineId(mockPipelineId);
                pipelineExecution.setPipelineUrl(mockPipelineUrl);
                pipelineExecution = pipelineExecutionRepository.save(pipelineExecution);

                logger.info("MOCK: First pipeline triggered successfully: {} for step {}", mockPipelineId, step.getId());

            } else {
                // Real GitLab API call
                GitLabApiClient.GitLabPipelineResponse response = null;
                try {
                    response = gitLabApiClient
                            .triggerPipeline(gitLabConfig.getBaseUrl(), application.getGitlabProjectId(),
                                           step.getBranch(), applicationService.getDecryptedPersonalAccessToken(application.getId()), pipelineVariables)
                            .doOnError(error -> {
                                logger.error("GitLab API call failed for first pipeline project {} on branch {}: {}",
                                           application.getGitlabProjectId(), step.getBranch(), error.getMessage());
                                if (error.getMessage().contains("400")) {
                                    logger.error("This is likely due to invalid GitLab project ID, branch name, or access token");
                                }
                            })
                            .block();
                } catch (Exception apiError) {
                    logger.error("GitLab API call failed for first pipeline: {}", apiError.getMessage());
                    response = null;
                }

                if (response != null) {
                    pipelineExecution.setPipelineId(response.getId());
                    pipelineExecution.setPipelineUrl(response.getWebUrl());
                    pipelineExecution = pipelineExecutionRepository.save(pipelineExecution);

                    logger.info("First pipeline triggered successfully: {} for step {}", response.getId(), step.getId());

                    // Note: Polling for completion will be handled by executeFlowAsync

                } else {
                    logger.error("Failed to trigger first pipeline - null response from GitLab API");
                    pipelineExecution.setStatus(ExecutionStatus.FAILED);
                    pipelineExecution.setEndTime(LocalDateTime.now());
                    pipelineExecution = pipelineExecutionRepository.save(pipelineExecution);
                }
            }

        } catch (Exception e) {
            logger.error("Failed to trigger first pipeline synchronously: {}", e.getMessage(), e);
            pipelineExecution.setStatus(ExecutionStatus.FAILED);
            pipelineExecution.setEndTime(LocalDateTime.now());
            pipelineExecution = pipelineExecutionRepository.save(pipelineExecution);
        }

        return pipelineExecution;
    }

    @Async("pipelinePollingTaskExecutor")
    public void pollPipelineCompletionAsync(PipelineExecution pipelineExecution, Application application, String gitlabBaseUrl, FlowStep step) {
        logger.info("Starting async polling for first pipeline completion: {}", pipelineExecution.getPipelineId());

        try {
            while (true) {
                GitLabApiClient.GitLabPipelineResponse status = gitLabApiClient
                        .getPipelineStatus(gitlabBaseUrl, application.getGitlabProjectId(),
                                         pipelineExecution.getPipelineId(), applicationService.getDecryptedPersonalAccessToken(application.getId()))
                        .block();

                if (status != null && status.isCompleted()) {
                    pipelineExecution.setStatus(status.isSuccessful() ? ExecutionStatus.PASSED : ExecutionStatus.FAILED);
                    pipelineExecution.setEndTime(LocalDateTime.now());

                    // Download artifacts if successful
                    if (status.isSuccessful()) {
                        downloadAndParseArtifacts(pipelineExecution, application, gitlabBaseUrl, step);
                    }

                    pipelineExecutionRepository.save(pipelineExecution);
                    logger.info("First pipeline {} completed with status: {}", pipelineExecution.getPipelineId(), pipelineExecution.getStatus());
                    break;
                }

                // Wait before next poll - configurable via application.yml
                Thread.sleep(flowExecutionPollingIntervalMs);
            }
        } catch (Exception e) {
            logger.error("Error polling first pipeline completion: {}", e.getMessage(), e);
            pipelineExecution.setStatus(ExecutionStatus.FAILED);
            pipelineExecution.setEndTime(LocalDateTime.now());
            pipelineExecutionRepository.save(pipelineExecution);
        }
    }

    @Async("flowExecutionTaskExecutor")
    public CompletableFuture<FlowExecutionDto> executeFlowAsync(UUID flowExecutionId) {
        logger.info("Starting async execution of flow execution ID: {}", flowExecutionId);

        // Set up logging context manually
        org.slf4j.MDC.put("flowExecutionId", flowExecutionId.toString());

        FlowExecution flowExecution = null;
        try {
            flowExecution = flowExecutionRepository.findById(flowExecutionId)
                    .orElseThrow(() -> new IllegalArgumentException("Flow execution not found with ID: " + flowExecutionId));

            final Long flowId = flowExecution.getFlowId();
            Flow flow = flowRepository.findById(flowId)
                    .orElseThrow(() -> new IllegalArgumentException("Flow not found with ID: " + flowId));

            // Execute steps sequentially, checking for scheduling
            Map<String, String> accumulatedRuntimeVariables = new HashMap<>();
            LocalDateTime previousStepEndTime = flowExecution.getStartTime() != null ? flowExecution.getStartTime() : LocalDateTime.now();

            for (int i = 0; i < flow.getFlowStepIds().size(); i++) {
                Long stepId = flow.getFlowStepIds().get(i);
                FlowStep step = flowStepRepository.findById(stepId)
                        .orElseThrow(() -> new IllegalArgumentException("Flow step not found with ID: " + stepId));

                Application application = step.getApplication();

                // Check if this pipeline step is already running or completed (from createFlowExecution trigger)
                PipelineExecution existingPipelineExecution = pipelineExecutionRepository
                        .findByFlowExecutionIdAndFlowStepId(flowExecution.getId(), stepId)
                        .orElse(null);

                if (existingPipelineExecution != null &&
                    (existingPipelineExecution.getStatus() == ExecutionStatus.RUNNING ||
                     existingPipelineExecution.getStatus() == ExecutionStatus.PASSED ||
                     existingPipelineExecution.getStatus() == ExecutionStatus.FAILED)) {
                    // Pipeline already triggered, just wait for completion and accumulate variables
                    logger.debug("Pipeline for step {} already triggered with status: {}, waiting for completion",
                               stepId, existingPipelineExecution.getStatus());

                    // Wait for completion if still running
                    if (existingPipelineExecution.getStatus() == ExecutionStatus.RUNNING) {
                        existingPipelineExecution = waitForPipelineCompletion(existingPipelineExecution, application, step);
                    }

                    if (existingPipelineExecution.getStatus() == ExecutionStatus.FAILED) {
                        // Mark flow as failed and stop execution
                        flowExecution.setStatus(ExecutionStatus.FAILED);
                        flowExecution.setEndTime(LocalDateTime.now());
                        flowExecution.setRuntimeVariables(accumulatedRuntimeVariables);
                        flowExecutionRepository.save(flowExecution);

                        logger.error("Flow execution failed at step: {}", stepId);
                        return CompletableFuture.completedFuture(convertToDto(flowExecution));
                    }

                    // Accumulate runtime variables from this step for next steps
                    if (existingPipelineExecution.getRuntimeTestData() != null) {
                        accumulatedRuntimeVariables.putAll(existingPipelineExecution.getRuntimeTestData());
                        logger.debug("Accumulated runtime variables after step {}: {}", stepId, accumulatedRuntimeVariables);
                    }

                    // Update previous step end time for scheduling calculations
                    if (existingPipelineExecution.getEndTime() != null) {
                        previousStepEndTime = existingPipelineExecution.getEndTime();
                    }

                    continue; // Skip to next step
                }

                // Check if step has invokeScheduler - if so, schedule it and stop execution here
                if (step.getInvokeScheduler() != null) {
                    LocalDateTime resumeTime = schedulingService.calculateResumeTime(previousStepEndTime, step.getInvokeScheduler());
                    if (resumeTime != null) {
                        logger.info("Step {} has invokeScheduler, scheduling for execution at: {} and stopping flow execution", stepId, resumeTime);

                        // Find existing pipeline execution record
                        PipelineExecution pipelineExecution = pipelineExecutionRepository
                                .findByFlowExecutionIdAndFlowStepId(flowExecution.getId(), stepId)
                                .orElseThrow(() -> new IllegalStateException("Pipeline execution record not found for step: " + stepId));

                        // Schedule the pipeline execution
                        schedulingService.schedulePipelineExecution(pipelineExecution, resumeTime);

                        // STOP execution here - don't continue to next steps
                        // The scheduler will handle resuming execution when this step completes
                        logger.info("Flow execution paused at scheduled step {}, will resume after completion", stepId);
                        return CompletableFuture.completedFuture(convertToDto(flowExecution));
                    } else {
                        logger.warn("Failed to calculate resume time for step {} with invokeScheduler, executing immediately", stepId);
                    }
                }

                // Prepare variables for this pipeline: FlowStep TestData + Accumulated Runtime
                Map<String, String> pipelineVariables = new HashMap<>();

                // 1. Start with merged test data from TestData table
                Map<String, String> stepTestData = testDataService.mergeTestDataByIds(step.getTestDataIds());
                pipelineVariables.putAll(stepTestData);

                // 2. Add accumulated runtime variables from previous steps (can override test data)
                pipelineVariables.putAll(accumulatedRuntimeVariables);

                logger.debug("Pipeline variables for step {}: {}", stepId, pipelineVariables);

                // Execute pipeline step
                PipelineExecution pipelineExecution = executePipelineStep(flowExecution, step, application, pipelineVariables);

                if (pipelineExecution.getStatus() == ExecutionStatus.FAILED) {
                    // Mark flow as failed and stop execution
                    flowExecution.setStatus(ExecutionStatus.FAILED);
                    flowExecution.setEndTime(LocalDateTime.now());
                    flowExecution.setRuntimeVariables(accumulatedRuntimeVariables);
                    flowExecutionRepository.save(flowExecution);

                    logger.error("Flow execution failed at step: {}", stepId);
                    return CompletableFuture.completedFuture(convertToDto(flowExecution));
                }

                // Accumulate runtime variables from this step for next steps
                if (pipelineExecution.getRuntimeTestData() != null) {
                    accumulatedRuntimeVariables.putAll(pipelineExecution.getRuntimeTestData());
                    logger.debug("Accumulated runtime variables after step {}: {}", stepId, accumulatedRuntimeVariables);
                }

                // Update previous step end time for scheduling calculations
                if (pipelineExecution.getEndTime() != null) {
                    previousStepEndTime = pipelineExecution.getEndTime();
                }
            }

            // Mark flow as successful
            flowExecution.setStatus(ExecutionStatus.PASSED);
            flowExecution.setEndTime(LocalDateTime.now());
            flowExecution.setRuntimeVariables(accumulatedRuntimeVariables);
            flowExecution = flowExecutionRepository.save(flowExecution);

            logger.info("Flow execution completed successfully: {}", flowExecution.getId());

            return CompletableFuture.completedFuture(convertToDto(flowExecution));
        } catch (Exception e) {
            logger.error("Flow execution failed with exception: {}", e.getMessage(), e);

            if (flowExecution != null) {
                flowExecution.setStatus(ExecutionStatus.FAILED);
                flowExecution.setEndTime(LocalDateTime.now());
                flowExecutionRepository.save(flowExecution);
            }
            // Propagate exception so that the CompletableFuture completes exceptionally
            throw new RuntimeException(e);
        } finally {
            org.slf4j.MDC.remove("flowExecutionId");
        }
    }

    public FlowExecutionDto createReplayFlowExecution(UUID originalFlowExecutionId, Long failedFlowStepId) {
        logger.info("Creating replay flow execution for original execution: {} from failed step: {}", originalFlowExecutionId, failedFlowStepId);

        FlowExecution originalExecution = flowExecutionRepository.findById(originalFlowExecutionId)
                .orElseThrow(() -> new IllegalArgumentException("Original flow execution not found with ID: " + originalFlowExecutionId));

        if (originalExecution.getStatus() != ExecutionStatus.FAILED) {
            throw new IllegalArgumentException("Can only replay failed flow executions");
        }

        Flow flow = flowRepository.findById(originalExecution.getFlowId())
                .orElseThrow(() -> new IllegalArgumentException("Flow not found with ID: " + originalExecution.getFlowId()));

        // Validate that the failed step exists in the flow
        if (!flow.getFlowStepIds().contains(failedFlowStepId)) {
            throw new IllegalArgumentException("Failed flow step ID " + failedFlowStepId + " is not part of flow " + flow.getId());
        }

        // Get all successful pipeline executions up to the failed step to extract runtime variables
        Map<String, String> accumulatedRuntimeVariables = extractRuntimeVariablesUpToStep(originalFlowExecutionId, failedFlowStepId, flow);

        // Create new flow execution record for replay
        FlowExecution replayExecution = new FlowExecution(originalExecution.getFlowId(), accumulatedRuntimeVariables);
        replayExecution.setIsReplay(true);
        replayExecution = flowExecutionRepository.save(replayExecution);

        // Build a map of original successful pipelines before the failed step (by flowStepId)
        int failedStepIndex = flow.getFlowStepIds().indexOf(failedFlowStepId);
        List<PipelineExecution> originalPipelinesOrdered = pipelineExecutionRepository
                .findByFlowExecutionIdOrderByCreatedAt(originalFlowExecutionId);
        Map<Long, PipelineExecution> originalPassedByStep = originalPipelinesOrdered.stream()
                .filter(pe -> pe.getStatus() == ExecutionStatus.PASSED)
                .collect(Collectors.toMap(PipelineExecution::getFlowStepId, pe -> pe, (a, b) -> a));

        // 1) Pre-create "carried" entries for steps BEFORE the failed step, mark PASSED and reference original pipeline/job
        for (int i = 0; i < failedStepIndex; i++) {
            Long stepId = flow.getFlowStepIds().get(i);
            FlowStep step = flowStepRepository.findById(stepId)
                    .orElseThrow(() -> new IllegalArgumentException("Flow step not found with ID: " + stepId));

            PipelineExecution originalPe = originalPassedByStep.get(stepId);
            PipelineExecution carried = new PipelineExecution();
            carried.setFlowId(replayExecution.getFlowId());
            carried.setFlowExecutionId(replayExecution.getId());
            carried.setFlowStepId(stepId);
            // preserve inputs/outputs from original successful step
            if (originalPe != null) {
                carried.setConfiguredTestData(originalPe.getConfiguredTestData());
                carried.setRuntimeTestData(originalPe.getRuntimeTestData());
                carried.setPipelineId(originalPe.getPipelineId());
                carried.setPipelineUrl(originalPe.getPipelineUrl());
                carried.setJobId(originalPe.getJobId());
                carried.setJobUrl(originalPe.getJobUrl());
                carried.setStartTime(originalPe.getStartTime());
                carried.setEndTime(originalPe.getEndTime());
            } else {
                // Fallback to current config if no original found
                carried.setConfiguredTestData(testDataService.mergeTestDataByIds(step.getTestDataIds()));
                carried.setRuntimeTestData(new HashMap<>(accumulatedRuntimeVariables));
            }
            carried.setStatus(ExecutionStatus.PASSED);
            carried.setIsReplay(false); // Carried steps are not replays - they're successful from original execution
            pipelineExecutionTxService.saveNew(carried);
        }

        // 2) Pre-create placeholders only for steps from failedStep onwards
        for (int i = failedStepIndex; i < flow.getFlowStepIds().size(); i++) {
            Long stepId = flow.getFlowStepIds().get(i);
            FlowStep step = flowStepRepository.findById(stepId)
                    .orElseThrow(() -> new IllegalArgumentException("Flow step not found with ID: " + stepId));
            PipelineExecution placeholder = new PipelineExecution();
            placeholder.setFlowId(replayExecution.getFlowId());
            placeholder.setFlowExecutionId(replayExecution.getId());
            placeholder.setFlowStepId(stepId);
            placeholder.setConfiguredTestData(testDataService.mergeTestDataByIds(step.getTestDataIds()));
            // Seed with accumulated variables present at replay start
            placeholder.setRuntimeTestData(new HashMap<>(accumulatedRuntimeVariables));
            placeholder.setStatus(ExecutionStatus.SCHEDULED);
            placeholder.setStartTime(null);
            placeholder.setIsReplay(true);
            pipelineExecutionTxService.saveNew(placeholder);
        }

        logger.info("Created replay flow execution with ID: {} for original execution: {} and pre-created placeholders from step {} onward", replayExecution.getId(), originalFlowExecutionId, failedFlowStepId);
        return convertToDto(replayExecution);
    }

    /**
     * Resume flow execution from a specific step onwards (used by scheduler when scheduled steps complete)
     */
    @Async("flowExecutionTaskExecutor")
    public void resumeFlowExecution(UUID flowExecutionId, Long completedStepId) {
        logger.info("Resuming flow execution ID: {} from step: {}", flowExecutionId, completedStepId);

        // Set up logging context manually
        org.slf4j.MDC.put("flowExecutionId", flowExecutionId.toString());

        FlowExecution flowExecution = null;
        try {
            flowExecution = flowExecutionRepository.findById(flowExecutionId)
                    .orElseThrow(() -> new IllegalArgumentException("Flow execution not found with ID: " + flowExecutionId));

            final Long flowId = flowExecution.getFlowId();
            Flow flow = flowRepository.findById(flowId)
                    .orElseThrow(() -> new IllegalArgumentException("Flow not found with ID: " + flowId));

            // Find the index of the completed step
            int completedStepIndex = -1;
            for (int i = 0; i < flow.getFlowStepIds().size(); i++) {
                if (flow.getFlowStepIds().get(i).equals(completedStepId)) {
                    completedStepIndex = i;
                    break;
                }
            }

            if (completedStepIndex == -1) {
                logger.error("Completed step {} not found in flow {}", completedStepId, flowId);
                return;
            }

            // Check if this is a scheduled step that was just activated (marked as IN_PROGRESS by scheduler)
            // If so, we need to execute it first before continuing to subsequent steps
            PipelineExecution scheduledStepExecution = pipelineExecutionRepository
                    .findByFlowExecutionIdAndFlowStepId(flowExecutionId, completedStepId)
                    .orElse(null);

            Map<String, String> accumulatedRuntimeVariables = new HashMap<>(flowExecution.getRuntimeVariables());
            LocalDateTime previousStepEndTime = LocalDateTime.now();

            // If this is a scheduled step that was just activated by the scheduler, execute it
            // Check if it's not already completed (PASSED or FAILED)
            if (scheduledStepExecution != null &&
                scheduledStepExecution.getStatus() != ExecutionStatus.PASSED &&
                scheduledStepExecution.getStatus() != ExecutionStatus.FAILED) {
                logger.info("Executing scheduled step {} that was just activated by scheduler", completedStepId);

                FlowStep scheduledStep = flowStepRepository.findById(completedStepId)
                        .orElseThrow(() -> new IllegalArgumentException("Flow step not found with ID: " + completedStepId));

                Application application = scheduledStep.getApplication();

                // Prepare variables for this scheduled step
                Map<String, String> pipelineVariables = new HashMap<>();
                Map<String, String> stepTestData = testDataService.mergeTestDataByIds(scheduledStep.getTestDataIds());
                pipelineVariables.putAll(stepTestData);
                pipelineVariables.putAll(accumulatedRuntimeVariables);

                logger.debug("Pipeline variables for scheduled step {}: {}", completedStepId, pipelineVariables);

                // Execute the scheduled step (this will trigger GitLab pipeline and wait for completion)
                scheduledStepExecution = executePipelineStep(flowExecution, scheduledStep, application, pipelineVariables);

                if (scheduledStepExecution.getStatus() == ExecutionStatus.FAILED) {
                    // Mark flow as failed and stop execution
                    flowExecution.setStatus(ExecutionStatus.FAILED);
                    flowExecution.setEndTime(LocalDateTime.now());
                    flowExecution.setRuntimeVariables(accumulatedRuntimeVariables);
                    flowExecutionRepository.save(flowExecution);
                    logger.error("Flow execution failed at scheduled step: {}", completedStepId);
                    return;
                }

                // Accumulate runtime variables from this step
                if (scheduledStepExecution.getRuntimeTestData() != null) {
                    accumulatedRuntimeVariables.putAll(scheduledStepExecution.getRuntimeTestData());
                    logger.debug("Accumulated runtime variables after scheduled step {}: {}", completedStepId, accumulatedRuntimeVariables);
                }

                // Update previous step end time
                if (scheduledStepExecution.getEndTime() != null) {
                    previousStepEndTime = scheduledStepExecution.getEndTime();
                }
            }

            // Continue execution from the next step onwards
            for (int i = completedStepIndex + 1; i < flow.getFlowStepIds().size(); i++) {
                Long stepId = flow.getFlowStepIds().get(i);
                FlowStep step = flowStepRepository.findById(stepId)
                        .orElseThrow(() -> new IllegalArgumentException("Flow step not found with ID: " + stepId));

                Application application = step.getApplication();

                // Check if this pipeline step is already running or completed
                PipelineExecution existingPipelineExecution = pipelineExecutionRepository
                        .findByFlowExecutionIdAndFlowStepId(flowExecution.getId(), stepId)
                        .orElse(null);

                if (existingPipelineExecution != null &&
                    existingPipelineExecution.getStatus() == ExecutionStatus.PASSED) {
                    // Pipeline already completed successfully, accumulate variables and continue
                    logger.debug("Pipeline for step {} already completed successfully", stepId);

                    // Accumulate runtime variables from this step for next steps
                    if (existingPipelineExecution.getRuntimeTestData() != null) {
                        accumulatedRuntimeVariables.putAll(existingPipelineExecution.getRuntimeTestData());
                        logger.debug("Accumulated runtime variables after step {}: {}", stepId, accumulatedRuntimeVariables);
                    }

                    // Update previous step end time
                    if (existingPipelineExecution.getEndTime() != null) {
                        previousStepEndTime = existingPipelineExecution.getEndTime();
                    }

                    continue; // Skip to next step
                }

                if (existingPipelineExecution != null &&
                    existingPipelineExecution.getStatus() == ExecutionStatus.FAILED) {
                    // Pipeline already failed, mark flow as failed and stop execution
                    logger.debug("Pipeline for step {} already failed", stepId);

                    flowExecution.setStatus(ExecutionStatus.FAILED);
                    flowExecution.setEndTime(LocalDateTime.now());
                    flowExecution.setRuntimeVariables(accumulatedRuntimeVariables);
                    flowExecutionRepository.save(flowExecution);

                    logger.error("Flow execution failed at step: {}", stepId);
                    return;
                }

                if (existingPipelineExecution != null &&
                    existingPipelineExecution.getStatus() == ExecutionStatus.RUNNING) {
                    // Pipeline is currently running, wait for completion
                    logger.debug("Pipeline for step {} is currently running, waiting for completion", stepId);
                    existingPipelineExecution = waitForPipelineCompletion(existingPipelineExecution, application, step);

                    if (existingPipelineExecution.getStatus() == ExecutionStatus.FAILED) {
                        // Mark flow as failed and stop execution
                        flowExecution.setStatus(ExecutionStatus.FAILED);
                        flowExecution.setEndTime(LocalDateTime.now());
                        flowExecution.setRuntimeVariables(accumulatedRuntimeVariables);
                        flowExecutionRepository.save(flowExecution);

                        logger.error("Flow execution failed at step: {}", stepId);
                        return;
                    }

                    // Accumulate runtime variables from this step for next steps
                    if (existingPipelineExecution.getRuntimeTestData() != null) {
                        accumulatedRuntimeVariables.putAll(existingPipelineExecution.getRuntimeTestData());
                        logger.debug("Accumulated runtime variables after step {}: {}", stepId, accumulatedRuntimeVariables);
                    }

                    // Update previous step end time
                    if (existingPipelineExecution.getEndTime() != null) {
                        previousStepEndTime = existingPipelineExecution.getEndTime();
                    }

                    continue; // Skip to next step
                }

                // Check if this is a scheduled step that was just marked as IN_PROGRESS by the scheduler
                if (existingPipelineExecution != null &&
                    existingPipelineExecution.getStatus() == ExecutionStatus.IN_PROGRESS) {
                    // This is a scheduled step that the scheduler just activated
                    // We need to actually trigger the GitLab pipeline now
                    logger.info("Executing scheduled step {} that was activated by scheduler", stepId);

                    // Prepare variables for this pipeline: FlowStep TestData + Accumulated Runtime
                    Map<String, String> pipelineVariables = new HashMap<>();

                    // 1. Start with merged test data from TestData table
                    Map<String, String> stepTestData = testDataService.mergeTestDataByIds(step.getTestDataIds());
                    pipelineVariables.putAll(stepTestData);

                    // 2. Add accumulated runtime variables from previous steps (can override test data)
                    pipelineVariables.putAll(accumulatedRuntimeVariables);

                    logger.debug("Pipeline variables for scheduled step {}: {}", stepId, pipelineVariables);

                    // Update existing pipeline execution and trigger GitLab pipeline
                    existingPipelineExecution.setStartTime(LocalDateTime.now());
                    existingPipelineExecution.setStatus(ExecutionStatus.RUNNING);
                    existingPipelineExecution = pipelineExecutionRepository.save(existingPipelineExecution);

                    // Execute pipeline step (this will trigger GitLab and wait for completion)
                    existingPipelineExecution = executePipelineStepInternal(flowExecution, step, application, pipelineVariables, existingPipelineExecution);

                    if (existingPipelineExecution.getStatus() == ExecutionStatus.FAILED) {
                        // Mark flow as failed and stop execution
                        flowExecution.setStatus(ExecutionStatus.FAILED);
                        flowExecution.setEndTime(LocalDateTime.now());
                        flowExecution.setRuntimeVariables(accumulatedRuntimeVariables);
                        flowExecutionRepository.save(flowExecution);

                        logger.error("Flow execution failed at scheduled step: {}", stepId);
                        return;
                    }

                    // Accumulate runtime variables from this step for next steps
                    if (existingPipelineExecution.getRuntimeTestData() != null) {
                        accumulatedRuntimeVariables.putAll(existingPipelineExecution.getRuntimeTestData());
                        logger.debug("Accumulated runtime variables after scheduled step {}: {}", stepId, accumulatedRuntimeVariables);
                    }

                    // Update previous step end time
                    if (existingPipelineExecution.getEndTime() != null) {
                        previousStepEndTime = existingPipelineExecution.getEndTime();
                    }

                    continue; // Continue to next step
                }

                // Check if step has invokeScheduler - if so, schedule it and stop execution here
                if (step.getInvokeScheduler() != null) {
                    LocalDateTime resumeTime = schedulingService.calculateResumeTime(previousStepEndTime, step.getInvokeScheduler());
                    if (resumeTime != null) {
                        logger.info("Step {} has invokeScheduler, scheduling for execution at: {} and stopping flow execution", stepId, resumeTime);

                        // Find existing pipeline execution record
                        PipelineExecution pipelineExecution = pipelineExecutionRepository
                                .findByFlowExecutionIdAndFlowStepId(flowExecution.getId(), stepId)
                                .orElseThrow(() -> new IllegalStateException("Pipeline execution record not found for step: " + stepId));

                        // Schedule the pipeline execution
                        schedulingService.schedulePipelineExecution(pipelineExecution, resumeTime);

                        // STOP execution here - don't continue to next steps
                        logger.info("Flow execution paused at scheduled step {}, will resume after completion", stepId);
                        return;
                    } else {
                        logger.warn("Failed to calculate resume time for step {} with invokeScheduler, executing immediately", stepId);
                    }
                }

                // Prepare variables for this pipeline: FlowStep TestData + Accumulated Runtime
                Map<String, String> pipelineVariables = new HashMap<>();

                // 1. Start with merged test data from TestData table
                Map<String, String> stepTestData = testDataService.mergeTestDataByIds(step.getTestDataIds());
                pipelineVariables.putAll(stepTestData);

                // 2. Add accumulated runtime variables from previous steps (can override test data)
                pipelineVariables.putAll(accumulatedRuntimeVariables);

                logger.debug("Pipeline variables for step {}: {}", stepId, pipelineVariables);

                // Execute pipeline step
                PipelineExecution pipelineExecution = executePipelineStep(flowExecution, step, application, pipelineVariables);

                if (pipelineExecution.getStatus() == ExecutionStatus.FAILED) {
                    // Mark flow as failed and stop execution
                    flowExecution.setStatus(ExecutionStatus.FAILED);
                    flowExecution.setEndTime(LocalDateTime.now());
                    flowExecution.setRuntimeVariables(accumulatedRuntimeVariables);
                    flowExecutionRepository.save(flowExecution);

                    logger.error("Flow execution failed at step: {}", stepId);
                    return;
                }

                // Accumulate runtime variables from this step for next steps
                if (pipelineExecution.getRuntimeTestData() != null) {
                    accumulatedRuntimeVariables.putAll(pipelineExecution.getRuntimeTestData());
                    logger.debug("Accumulated runtime variables after step {}: {}", stepId, accumulatedRuntimeVariables);
                }

                // Update previous step end time
                if (pipelineExecution.getEndTime() != null) {
                    previousStepEndTime = pipelineExecution.getEndTime();
                }
            }

            // Mark flow as successful
            flowExecution.setStatus(ExecutionStatus.PASSED);
            flowExecution.setEndTime(LocalDateTime.now());
            flowExecution.setRuntimeVariables(accumulatedRuntimeVariables);
            flowExecution = flowExecutionRepository.save(flowExecution);

            logger.info("Flow execution completed successfully: {}", flowExecution.getId());

        } catch (Exception e) {
            logger.error("Flow execution resume failed with exception: {}", e.getMessage(), e);

            if (flowExecution != null) {
                flowExecution.setStatus(ExecutionStatus.FAILED);
                flowExecution.setEndTime(LocalDateTime.now());
                flowExecutionRepository.save(flowExecution);
            }
        } finally {
            org.slf4j.MDC.remove("flowExecutionId");
        }
    }

    @Async("flowExecutionTaskExecutor")
    public CompletableFuture<FlowExecutionDto> executeReplayFlowAsync(UUID replayFlowExecutionId, UUID originalFlowExecutionId, Long failedFlowStepId) {
        logger.info("Starting async replay execution of flow execution ID: {} from step: {}", replayFlowExecutionId, failedFlowStepId);

        // Set up logging context manually
        org.slf4j.MDC.put("flowExecutionId", replayFlowExecutionId.toString());

        FlowExecution replayExecution = null;
        try {
            replayExecution = flowExecutionRepository.findById(replayFlowExecutionId)
                    .orElseThrow(() -> new IllegalArgumentException("Replay flow execution not found with ID: " + replayFlowExecutionId));

            final Long flowId = replayExecution.getFlowId();
            Flow flow = flowRepository.findById(flowId)
                    .orElseThrow(() -> new IllegalArgumentException("Flow not found with ID: " + flowId));

            // Start execution from the failed step onwards
            Map<String, String> accumulatedRuntimeVariables = new HashMap<>(replayExecution.getRuntimeVariables());
            int failedStepIndex = flow.getFlowStepIds().indexOf(failedFlowStepId);

            for (int i = failedStepIndex; i < flow.getFlowStepIds().size(); i++) {
                Long stepId = flow.getFlowStepIds().get(i);
                FlowStep step = flowStepRepository.findById(stepId)
                        .orElseThrow(() -> new IllegalArgumentException("Flow step not found with ID: " + stepId));

                Application application = step.getApplication();

                // Only execute pipeline steps from failed step onwards - skip successful steps
                if (i < failedStepIndex) {
                    logger.debug("Skipping successful step {} in replay execution", stepId);
                    continue;
                }

                // Prepare variables for this pipeline: FlowStep TestData + Accumulated Runtime
                Map<String, String> pipelineVariables = new HashMap<>();

                // 1. Start with merged test data from TestData table
                Map<String, String> stepTestData = testDataService.mergeTestDataByIds(step.getTestDataIds());
                pipelineVariables.putAll(stepTestData);

                // 2. Add accumulated runtime variables from previous steps (can override test data)
                pipelineVariables.putAll(accumulatedRuntimeVariables);

                logger.debug("Replay pipeline variables for step {}: {}", stepId, pipelineVariables);

                // Execute pipeline step with replay flag
                PipelineExecution pipelineExecution = executeReplayPipelineStep(replayExecution, step, application, pipelineVariables);

                if (pipelineExecution.getStatus() == ExecutionStatus.FAILED) {
                    // Mark replay flow as failed and stop execution
                    replayExecution.setStatus(ExecutionStatus.FAILED);
                    replayExecution.setEndTime(LocalDateTime.now());
                    replayExecution.setRuntimeVariables(accumulatedRuntimeVariables);
                    flowExecutionRepository.save(replayExecution);

                    logger.error("Replay flow execution failed at step: {}", stepId);
                    return CompletableFuture.completedFuture(convertToDto(replayExecution));
                }

                // Accumulate runtime variables from this step for next steps
                if (pipelineExecution.getRuntimeTestData() != null) {
                    accumulatedRuntimeVariables.putAll(pipelineExecution.getRuntimeTestData());
                    logger.debug("Accumulated runtime variables after replay step {}: {}", stepId, accumulatedRuntimeVariables);
                }
            }

            // Mark replay flow as successful
            replayExecution.setStatus(ExecutionStatus.PASSED);
            replayExecution.setEndTime(LocalDateTime.now());
            replayExecution.setRuntimeVariables(accumulatedRuntimeVariables);
            replayExecution = flowExecutionRepository.save(replayExecution);

            logger.info("Replay flow execution completed successfully: {}", replayExecution.getId());

            return CompletableFuture.completedFuture(convertToDto(replayExecution));
        } catch (Exception e) {
            logger.error("Replay flow execution failed with exception: {}", e.getMessage(), e);

            if (replayExecution != null) {
                replayExecution.setStatus(ExecutionStatus.FAILED);
                replayExecution.setEndTime(LocalDateTime.now());
                flowExecutionRepository.save(replayExecution);
            }
            // Propagate exception so that the CompletableFuture completes exceptionally
            throw new RuntimeException(e);
        } finally {
            org.slf4j.MDC.remove("flowExecutionId");
        }
    }

    private Map<String, String> extractRuntimeVariablesUpToStep(UUID originalFlowExecutionId, Long failedFlowStepId, Flow flow) {
        Map<String, String> accumulatedVariables = new HashMap<>();

        // Get the index of the failed step
        int failedStepIndex = flow.getFlowStepIds().indexOf(failedFlowStepId);

        // Get all successful pipeline executions from the original flow execution up to (but not including) the failed step
        List<PipelineExecution> successfulPipelines = pipelineExecutionRepository.findByFlowExecutionIdOrderByCreatedAt(originalFlowExecutionId)
                .stream()
                .filter(pe -> pe.getStatus() == ExecutionStatus.PASSED)
                .filter(pe -> {
                    int stepIndex = flow.getFlowStepIds().indexOf(pe.getFlowStepId());
                    return stepIndex < failedStepIndex;
                })
                .collect(Collectors.toList());

        // Accumulate runtime variables from successful steps in order
        for (PipelineExecution pipeline : successfulPipelines) {
            if (pipeline.getRuntimeTestData() != null) {
                accumulatedVariables.putAll(pipeline.getRuntimeTestData());
            }
        }

        logger.info("Extracted {} runtime variables from {} successful steps before failed step {}", 
                   accumulatedVariables.size(), successfulPipelines.size(), failedFlowStepId);

        return accumulatedVariables;
    }

    private PipelineExecution executeReplayPipelineStep(FlowExecution flowExecution, FlowStep step,
                                                       Application application, Map<String, String> pipelineVariables) {
        logger.info("Executing REPLAY pipeline step: {} for flow execution: {}", step.getId(), flowExecution.getId());

        // Use the already merged pipeline variables (Global + FlowStep + Runtime)
        Map<String, String> mergedVariables = new HashMap<>(pipelineVariables);

        // Add the testTag from FlowStep to make it available in GitLab pipeline scope
        if (step.getTestTag() != null && !step.getTestTag().trim().isEmpty()) {
            mergedVariables.put("testTag", step.getTestTag());
            logger.debug("Added testTag '{}' to replay pipeline variables for step {}", step.getTestTag(), step.getId());
        }

        // Add flowExecutionUuid and applicationName to make them available in GitLab pipeline scope
        mergedVariables.put("EXECUTION_UUID", flowExecution.getId().toString());
        mergedVariables.put("APP_NAME", application.getApplicationName());
        logger.debug("Added EXECUTION_UUID '{}' and APP_NAME '{}' to replay pipeline variables for step {}",
                    flowExecution.getId(), application.getApplicationName(), step.getId());

        // Find existing pipeline execution record created in createReplayFlowExecution
        PipelineExecution pipelineExecution = pipelineExecutionRepository
                .findByFlowExecutionIdAndFlowStepId(flowExecution.getId(), step.getId())
                .orElseThrow(() -> new IllegalStateException("Replay pipeline execution record not found for step: " + step.getId()));

        // Update the existing record instead of creating new one
        pipelineExecution.setStatus(ExecutionStatus.RUNNING);
        pipelineExecution.setStartTime(LocalDateTime.now());
        pipelineExecution.setIsReplay(true);
        pipelineExecution = pipelineExecutionRepository.save(pipelineExecution);

        try {
            logger.debug("Triggering REPLAY pipeline with variables: {}", mergedVariables);
            
            if (gitLabConfig.isMockMode()) {
                // Mock mode for testing
                logger.info("MOCK MODE: Simulating REPLAY GitLab pipeline execution for project {} on branch {}", 
                           application.getGitlabProjectId(), step.getBranch());
                
                // Simulate pipeline response
                long mockPipelineId = System.currentTimeMillis();
                String mockPipelineUrl = String.format("https://gitlab.com/%s/-/pipelines/%d", 
                                                      application.getGitlabProjectId(), mockPipelineId);
                
                pipelineExecution.setPipelineId(mockPipelineId);
                pipelineExecution.setPipelineUrl(mockPipelineUrl);
                pipelineExecution = pipelineExecutionRepository.save(pipelineExecution);
                
                logger.info("MOCK: REPLAY Pipeline triggered successfully: {} for step {}", mockPipelineId, step.getId());
                
                // Simulate successful completion after a short delay
                simulateMockPipelineCompletion(pipelineExecution);
                
            } else {
                // Real GitLab API call
                GitLabApiClient.GitLabPipelineResponse response = null;
                try {
                    response = gitLabApiClient
                            .triggerPipeline(gitLabConfig.getBaseUrl(), application.getGitlabProjectId(), 
                                           step.getBranch(), applicationService.getDecryptedPersonalAccessToken(application.getId()), mergedVariables)
                            .doOnError(error -> {
                                logger.error("GitLab API call failed for REPLAY project {} on branch {}: {}", 
                                           application.getGitlabProjectId(), step.getBranch(), error.getMessage());
                                if (error.getMessage().contains("400")) {
                                    logger.error("This is likely due to invalid GitLab project ID, branch name, or access token");
                                    logger.error("Please verify: 1) Project ID exists 2) Branch exists 3) Access token has API permissions");
                                }
                            })
                            .block();
                } catch (Exception apiError) {
                    logger.error("GitLab API call failed for REPLAY: {}", apiError.getMessage());
                    response = null;
                }
                
                if (response != null) {
                    pipelineExecution.setPipelineId(response.getId());
                    pipelineExecution.setPipelineUrl(response.getWebUrl());
                    pipelineExecution = pipelineExecutionRepository.save(pipelineExecution);
                    
                    logger.info("REPLAY Pipeline triggered successfully: {} for step {}", response.getId(), step.getId());
                    
                    // Poll for completion
                    pollPipelineCompletion(pipelineExecution, application, gitLabConfig.getBaseUrl(), step);
                } else {
                    logger.error("Failed to trigger REPLAY pipeline - null response from GitLab API");
                    pipelineExecution.setStatus(ExecutionStatus.FAILED);
                    pipelineExecution.setEndTime(LocalDateTime.now());
                    pipelineExecutionRepository.save(pipelineExecution);
                }
            }
            
        } catch (Exception e) {
            logger.error("Failed to execute REPLAY pipeline step: {}", e.getMessage(), e);
            pipelineExecution.setStatus(ExecutionStatus.FAILED);
            pipelineExecution.setEndTime(LocalDateTime.now());
            pipelineExecutionRepository.save(pipelineExecution);
        }
        
        return pipelineExecution;
    }

    private PipelineExecution executePipelineStep(FlowExecution flowExecution, FlowStep step,
                                                 Application application, Map<String, String> pipelineVariables) {
        logger.info("Executing pipeline step: {} for flow execution: {}", step.getId(), flowExecution.getId());

        // Find existing pipeline execution record created in createFlowExecution
        PipelineExecution pipelineExecution = pipelineExecutionRepository
                .findByFlowExecutionIdAndFlowStepId(flowExecution.getId(), step.getId())
                .orElseThrow(() -> new IllegalStateException("Pipeline execution record not found for step: " + step.getId()));

        return executePipelineStepInternal(flowExecution, step, application, pipelineVariables, pipelineExecution);
    }

    private PipelineExecution executePipelineStepInternal(FlowExecution flowExecution, FlowStep step,
                                                        Application application, Map<String, String> pipelineVariables,
                                                        PipelineExecution existingPipelineExecution) {
        logger.info("Executing pipeline step (internal): {} for flow execution: {}", step.getId(), flowExecution.getId());

        // Use the already merged pipeline variables (Global + FlowStep + Runtime)
        Map<String, String> mergedVariables = new HashMap<>(pipelineVariables);

        // Add the testTag from FlowStep to make it available in GitLab pipeline scope
        if (step.getTestTag() != null && !step.getTestTag().trim().isEmpty()) {
            mergedVariables.put("testTag", step.getTestTag());
            logger.debug("Added testTag '{}' to pipeline variables for step {}", step.getTestTag(), step.getId());
        }

        // Add flowExecutionUuid and applicationName to make them available in GitLab pipeline scope
        mergedVariables.put("EXECUTION_UUID", flowExecution.getId().toString());
        mergedVariables.put("APP_NAME", application.getApplicationName());
        logger.debug("Added EXECUTION_UUID '{}' and APP_NAME '{}' to pipeline variables for step {}",
                    flowExecution.getId(), application.getApplicationName(), step.getId());

        // Use the provided existing pipeline execution record
        PipelineExecution pipelineExecution = existingPipelineExecution;

        // Update the existing record
        pipelineExecution.setStatus(ExecutionStatus.RUNNING);
        pipelineExecution.setStartTime(LocalDateTime.now());
        pipelineExecution = pipelineExecutionRepository.save(pipelineExecution);

        try {
            logger.debug("Triggering pipeline with variables: {}", mergedVariables);
            
            if (gitLabConfig.isMockMode()) {
                // Mock mode for testing
                logger.info("MOCK MODE: Simulating GitLab pipeline execution for project {} on branch {}", 
                           application.getGitlabProjectId(), step.getBranch());
                
                // Simulate pipeline response
                long mockPipelineId = System.currentTimeMillis();
                String mockPipelineUrl = String.format("https://gitlab.com/%s/-/pipelines/%d", 
                                                      application.getGitlabProjectId(), mockPipelineId);
                
                pipelineExecution.setPipelineId(mockPipelineId);
                pipelineExecution.setPipelineUrl(mockPipelineUrl);
                pipelineExecution = pipelineExecutionRepository.save(pipelineExecution);
                
                logger.info("MOCK: Pipeline triggered successfully: {} for step {}", mockPipelineId, step.getId());
                
                // Simulate successful completion after a short delay
                simulateMockPipelineCompletion(pipelineExecution);
                
            } else {
                // Real GitLab API call
                GitLabApiClient.GitLabPipelineResponse response = null;
                try {
                    response = gitLabApiClient
                            .triggerPipeline(gitLabConfig.getBaseUrl(), application.getGitlabProjectId(), 
                                           step.getBranch(), applicationService.getDecryptedPersonalAccessToken(application.getId()), mergedVariables)
                            .doOnError(error -> {
                                logger.error("GitLab API call failed for project {} on branch {}: {}", 
                                           application.getGitlabProjectId(), step.getBranch(), error.getMessage());
                                if (error.getMessage().contains("400")) {
                                    logger.error("This is likely due to invalid GitLab project ID, branch name, or access token");
                                    logger.error("Please verify: 1) Project ID exists 2) Branch exists 3) Access token has API permissions");
                                }
                            })
                            .block();
                } catch (Exception apiError) {
                    logger.error("GitLab API call failed: {}", apiError.getMessage());
                    response = null;
                }
                
                if (response != null) {
                    pipelineExecution.setPipelineId(response.getId());
                    pipelineExecution.setPipelineUrl(response.getWebUrl());
                    pipelineExecution = pipelineExecutionRepository.save(pipelineExecution);
                    
                    logger.info("Pipeline triggered successfully: {} for step {}", response.getId(), step.getId());
                    
                    // Poll for completion
                    pollPipelineCompletion(pipelineExecution, application, gitLabConfig.getBaseUrl(), step);
                } else {
                    logger.error("Failed to trigger pipeline - null response from GitLab API");
                    pipelineExecution.setStatus(ExecutionStatus.FAILED);
                    pipelineExecution.setEndTime(LocalDateTime.now());
                    pipelineExecutionRepository.save(pipelineExecution);
                }
            }
            
        } catch (Exception e) {
            logger.error("Failed to execute pipeline step: {}", e.getMessage(), e);
            pipelineExecution.setStatus(ExecutionStatus.FAILED);
            pipelineExecution.setEndTime(LocalDateTime.now());
            pipelineExecutionRepository.save(pipelineExecution);
        }
        
        return pipelineExecution;
    }

    private void downloadAndParseArtifacts(PipelineExecution pipelineExecution, Application application, 
                                         String gitlabBaseUrl, FlowStep step) {
        try {
            // Get pipeline jobs
            GitLabApiClient.GitLabJobsResponse[] jobs = gitLabApiClient
                    .getPipelineJobs(gitlabBaseUrl, application.getGitlabProjectId(),
                                   pipelineExecution.getPipelineId(), applicationService.getDecryptedPersonalAccessToken(application.getId()))
                    .block();
            
            if (jobs != null && jobs.length > 0) {
                // Find job for the specified test stage
                GitLabApiClient.GitLabJobsResponse targetJob = null;
                for (GitLabApiClient.GitLabJobsResponse job : jobs) {
                    if (step.getTestStage().equals(job.getStage()) && job.isSuccessful()) {
                        targetJob = job;
                        break;
                    }
                }
                
                if (targetJob != null) {
                    logger.info("Found target job {} in stage {} for pipeline {}",
                               targetJob.getId(), targetJob.getStage(), pipelineExecution.getPipelineId());

                    // Set job information
                    pipelineExecution.setJobId(targetJob.getId());
                    pipelineExecution.setJobUrl(targetJob.getWebUrl());
                    pipelineExecutionRepository.save(pipelineExecution);

                    // Download output.env from target/output.env
                    String artifactContent = gitLabApiClient
                            .downloadJobArtifact(gitlabBaseUrl, application.getGitlabProjectId(),
                                               targetJob.getId(), applicationService.getDecryptedPersonalAccessToken(application.getId()), "target/output.env")
                            .block();

                    if (artifactContent != null && !artifactContent.trim().isEmpty()) {
                        Map<String, String> parsedVariables = outputEnvParser.parseOutputEnv(artifactContent);

                        // Merge configured test data with artifact data
                        Map<String, String> runtimeTestData = new HashMap<>();
                        if (pipelineExecution.getConfiguredTestData() != null) {
                            runtimeTestData.putAll(pipelineExecution.getConfiguredTestData());
                        }
                        runtimeTestData.putAll(parsedVariables); // Artifact data can override configured data

                        pipelineExecution.setRuntimeTestData(runtimeTestData);
                        pipelineExecutionRepository.save(pipelineExecution);
                        logger.info("Successfully downloaded and parsed artifacts from job {}: {} variables (total runtime: {})",
                                   targetJob.getId(), parsedVariables.size(), runtimeTestData.size());
                    } else {
                        logger.info("No artifact content found in job {}, runtime data will remain as configured data",
                                   targetJob.getId());
                        // Set runtime data to configured data if no artifacts
                        pipelineExecution.setRuntimeTestData(pipelineExecution.getConfiguredTestData());
                        pipelineExecutionRepository.save(pipelineExecution);
                    }
                } else {
                    logger.info("No successful job found for stage '{}' in pipeline {}",
                               step.getTestStage(), pipelineExecution.getPipelineId());
                }
            } else {
                logger.info("No jobs found for pipeline {}", pipelineExecution.getPipelineId());
            }
        } catch (Exception e) {
            // Don't treat missing artifacts as an error - just log and continue
            logger.info("No artifacts available for pipeline {} in stage '{}' (this is normal if stage doesn't generate output.env): {}", 
                       pipelineExecution.getPipelineId(), step.getTestStage(), e.getMessage());
        }
    }

    private void simulateMockPipelineCompletion(PipelineExecution pipelineExecution) {
        logger.info("MOCK: Simulating pipeline completion for pipeline: {}", pipelineExecution.getPipelineId());
        
        try {
            // Simulate pipeline execution time (2-5 seconds)
            Thread.sleep(2000 + (long)(Math.random() * 3000));
            
            // Simulate successful completion
            pipelineExecution.setStatus(ExecutionStatus.PASSED);
            pipelineExecution.setEndTime(LocalDateTime.now());
            
            // Simulate output.env data and merge with configured data
            Map<String, String> mockOutputData = new HashMap<>();
            mockOutputData.put("MOCK_USER_ID", "user_" + System.currentTimeMillis());
            mockOutputData.put("MOCK_SESSION_TOKEN", "token_" + UUID.randomUUID().toString().substring(0, 8));
            mockOutputData.put("MOCK_TRANSACTION_ID", "txn_" + System.currentTimeMillis());

            // Merge configured test data with mock artifact data
            Map<String, String> runtimeTestData = new HashMap<>();
            if (pipelineExecution.getConfiguredTestData() != null) {
                runtimeTestData.putAll(pipelineExecution.getConfiguredTestData());
            }
            runtimeTestData.putAll(mockOutputData); // Mock data can override configured data

            pipelineExecution.setRuntimeTestData(runtimeTestData);
            pipelineExecutionRepository.save(pipelineExecution);
            
            logger.info("MOCK: Pipeline {} completed successfully with mock data: {}", 
                       pipelineExecution.getPipelineId(), mockOutputData);
            
        } catch (InterruptedException e) {
            logger.error("Mock pipeline simulation interrupted", e);
            pipelineExecution.setStatus(ExecutionStatus.FAILED);
            pipelineExecution.setEndTime(LocalDateTime.now());
            pipelineExecutionRepository.save(pipelineExecution);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Wait for a pipeline that is already running to complete
     */
    private PipelineExecution waitForPipelineCompletion(PipelineExecution pipelineExecution, Application application, FlowStep step) {
        logger.info("Waiting for already running pipeline {} to complete", pipelineExecution.getPipelineId());

        try {
            while (true) {
                GitLabApiClient.GitLabPipelineResponse status = gitLabApiClient
                        .getPipelineStatus(gitLabConfig.getBaseUrl(), application.getGitlabProjectId(),
                                         pipelineExecution.getPipelineId(), applicationService.getDecryptedPersonalAccessToken(application.getId()))
                        .block();

                if (status != null && status.isCompleted()) {
                    pipelineExecution.setStatus(status.isSuccessful() ? ExecutionStatus.PASSED : ExecutionStatus.FAILED);
                    pipelineExecution.setEndTime(LocalDateTime.now());

                    // Download artifacts if successful
                    if (status.isSuccessful()) {
                        downloadAndParseArtifacts(pipelineExecution, application, gitLabConfig.getBaseUrl(), step);
                    }

                    pipelineExecution = pipelineExecutionRepository.save(pipelineExecution);
                    logger.info("Pipeline {} completed with status: {}", pipelineExecution.getPipelineId(), pipelineExecution.getStatus());
                    break;
                }

                // Wait before next poll - configurable via application.yml
                Thread.sleep(flowExecutionPollingIntervalMs);
            }
        } catch (Exception e) {
            logger.error("Error waiting for pipeline completion: {}", e.getMessage(), e);
            pipelineExecution.setStatus(ExecutionStatus.FAILED);
            pipelineExecution.setEndTime(LocalDateTime.now());
            pipelineExecution = pipelineExecutionRepository.save(pipelineExecution);
        }

        return pipelineExecution;
    }

    @Async("pipelinePollingTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void pollPipelineCompletion(PipelineExecution pipelineExecution, Application application, String gitlabBaseUrl, FlowStep step) {
        logger.info("Starting to poll pipeline completion for pipeline: {}", pipelineExecution.getPipelineId());

        try {
            while (true) {
                GitLabApiClient.GitLabPipelineResponse status = gitLabApiClient
                        .getPipelineStatus(gitlabBaseUrl, application.getGitlabProjectId(),
                                         pipelineExecution.getPipelineId(), applicationService.getDecryptedPersonalAccessToken(application.getId()))
                        .block();

                if (status != null && status.isCompleted()) {
                    pipelineExecution.setStatus(status.isSuccessful() ? ExecutionStatus.PASSED : ExecutionStatus.FAILED);
                    pipelineExecution.setEndTime(LocalDateTime.now());

                    // Download artifacts if successful
                    if (status.isSuccessful()) {
                        downloadAndParseArtifacts(pipelineExecution, application, gitlabBaseUrl, step);
                    }

                    pipelineExecutionRepository.save(pipelineExecution);
                    logger.info("Pipeline {} completed with status: {}", pipelineExecution.getPipelineId(), pipelineExecution.getStatus());
                    break;
                }

                // Wait before next poll - configurable via application.yml
                Thread.sleep(flowExecutionPollingIntervalMs);
            }
        } catch (Exception e) {
            logger.error("Error polling pipeline completion: {}", e.getMessage(), e);
            pipelineExecution.setStatus(ExecutionStatus.FAILED);
            pipelineExecution.setEndTime(LocalDateTime.now());
            pipelineExecutionRepository.save(pipelineExecution);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<FlowExecutionDto> getFlowExecutionById(UUID flowExecutionId) {
        logger.debug("Fetching flow execution with ID: {}", flowExecutionId);
        return flowExecutionRepository.findById(flowExecutionId)
                .map(this::convertToDtoWithDetails);
    }

    @Transactional(readOnly = true)
    public List<FlowExecutionDto> getFlowExecutionsByFlowId(Long flowId) {
        logger.debug("Fetching flow executions for flow ID: {}", flowId);
        return flowExecutionRepository.findByFlowId(flowId)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<FlowExecutionDto> getFlowExecutionsByFlowId(Long flowId, Pageable pageable) {
        logger.debug("Fetching flow executions for flow ID: {} with pagination: {}", flowId, pageable);
        return flowExecutionRepository.findByFlowId(flowId, pageable)
                .map(this::convertToDto);
    }

    private FlowExecutionDto convertToDto(FlowExecution entity) {
        FlowExecutionDto dto = new FlowExecutionDto();
        dto.setId(entity.getId());
        dto.setFlowId(entity.getFlowId());
        dto.setStartTime(entity.getStartTime());
        dto.setEndTime(entity.getEndTime());
        dto.setRuntimeVariables(entity.getRuntimeVariables());
        dto.setStatus(entity.getStatus());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setIsReplay(entity.getIsReplay());
        dto.setCategory(entity.getCategory());
        if (entity.getFlowGroup() != null) {
            dto.setFlowGroupId(entity.getFlowGroup().getId());
        }
        
        // Populate flowGroupName if flowGroupId is present
        if (entity.getFlowGroup() != null) {
            flowGroupRepository.findById(entity.getFlowGroup().getId()).ifPresent(flowGroup -> {
                dto.setFlowGroupName(flowGroup.getFlowGroupName());
            });
        }
        
        dto.setIteration(entity.getIteration());
        dto.setRevolutions(entity.getRevolutions());
        return dto;
    }

    private FlowExecutionDto convertToDtoWithDetails(FlowExecution entity) {
        FlowExecutionDto dto = convertToDto(entity);

        // Load flow details
        flowRepository.findById(entity.getFlowId()).ifPresent(flow -> {
            dto.setFlow(convertFlowToDto(flow));

            // Load flow steps
            List<FlowStep> flowSteps = flowStepRepository.findByIdIn(flow.getFlowStepIds());
            dto.setFlowSteps(flowSteps.stream().map(this::convertFlowStepToDto).collect(Collectors.toList()));

            // Load applications
            List<Long> applicationIds = flowSteps.stream().map(step -> step.getApplication().getId()).distinct().collect(Collectors.toList());
            List<Application> applications = applicationRepository.findAllById(applicationIds);
            dto.setApplications(applications.stream().map(this::convertApplicationToDto).collect(Collectors.toList()));

            // Load pipeline executions - show ALL configured FlowSteps with real-time status
            List<PipelineExecutionDto> allPipelineExecutions = getAllPipelineExecutionsForFlowExecution(entity.getId(), flow, flowSteps);
            dto.setPipelineExecutions(allPipelineExecutions);
        });

        return dto;
    }

    /**
     * Get all pipeline executions for a flow execution, ensuring ALL configured FlowSteps are represented
     * with appropriate status, even if not yet executed.
     */
    private List<PipelineExecutionDto> getAllPipelineExecutionsForFlowExecution(UUID flowExecutionId, Flow flow, List<FlowStep> flowSteps) {
        // Get existing pipeline executions for this flow execution - ensure fresh data
        List<PipelineExecution> existingPipelineExecutions = pipelineExecutionRepository.findByFlowExecutionIdOrderByCreatedAt(flowExecutionId);

        // Create a map of existing executions by flowStepId for quick lookup
        Map<Long, PipelineExecution> existingByStepId = existingPipelineExecutions.stream()
                .collect(Collectors.toMap(PipelineExecution::getFlowStepId, pe -> pe, (a, b) -> a));

        // Build complete list of pipeline executions for all configured FlowSteps in order
        List<PipelineExecutionDto> allPipelineExecutions = new ArrayList<>();

        for (FlowStep flowStep : flowSteps) {
            Long stepId = flowStep.getId();

            if (existingByStepId.containsKey(stepId)) {
                // Use existing pipeline execution data - this will have updated status
                PipelineExecution existing = existingByStepId.get(stepId);
                logger.debug("Found existing pipeline execution for step {} with status: {}", stepId, existing.getStatus());
                allPipelineExecutions.add(convertPipelineExecutionToDto(existing));
            } else {
                // Create placeholder PipelineExecutionDto for FlowSteps not yet executed
                PipelineExecutionDto placeholder = new PipelineExecutionDto();
                placeholder.setId(null); // No database record yet
                placeholder.setFlowId(flow.getId());
                placeholder.setFlowExecutionId(flowExecutionId);
                placeholder.setFlowStepId(stepId);
                placeholder.setPipelineId(null);
                placeholder.setPipelineUrl(null);
                placeholder.setStartTime(null);
                placeholder.setEndTime(null);
                // Set configured test data (what will be used as input)
                placeholder.setConfiguredTestData(testDataService.mergeTestDataByIds(flowStep.getTestDataIds()));
                placeholder.setRuntimeTestData(null); // No runtime data yet
                placeholder.setStatus(ExecutionStatus.SCHEDULED); // Waiting to be executed
                placeholder.setCreatedAt(null);
                placeholder.setIsReplay(false);
                allPipelineExecutions.add(placeholder);
            }
        }

        logger.debug("Returning {} pipeline executions for flow execution {}", allPipelineExecutions.size(), flowExecutionId);
        return allPipelineExecutions;
    }

    private FlowDto convertFlowToDto(Flow entity) {
        FlowDto dto = new FlowDto();
        dto.setId(entity.getId());
        dto.setFlowStepIds(entity.getFlowStepIds());
        dto.setSquashTestCaseId(entity.getSquashTestCaseId());
        dto.setSquashTestCase(entity.getSquashTestCase());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private FlowStepDto convertFlowStepToDto(FlowStep entity) {
        FlowStepDto dto = new FlowStepDto();
        dto.setId(entity.getId());
        dto.setApplicationId(entity.getApplication().getId());
        dto.setBranch(entity.getBranch());
        dto.setTestTag(entity.getTestTag());
        dto.setTestStage(entity.getTestStage());
        dto.setSquashStepIds(entity.getSquashStepIds());
        dto.setTestDataIds(entity.getTestDataIds());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        
        // Handle optional invokeScheduler
        if (entity.getInvokeScheduler() != null) {
            dto.setInvokeScheduler(convertInvokeSchedulerEntityToDto(entity.getInvokeScheduler()));
        }
        
        return dto;
    }

    private InvokeSchedulerDto convertInvokeSchedulerEntityToDto(InvokeScheduler entity) {
        if (entity == null) return null;
        
        InvokeSchedulerDto dto = new InvokeSchedulerDto();
        dto.setType(entity.getType());
        
        if (entity.getTimer() != null) {
            TimerDto timerDto = new TimerDto();
            timerDto.setMinutes(entity.getTimer().getMinutes());
            timerDto.setHours(entity.getTimer().getHours());
            timerDto.setDays(entity.getTimer().getDays());
            dto.setTimer(timerDto);
        }
        
        return dto;
    }

    private ApplicationDto convertApplicationToDto(Application entity) {
        ApplicationDto dto = new ApplicationDto();
        dto.setId(entity.getId());
        dto.setGitlabProjectId(entity.getGitlabProjectId());
        // Don't set personalAccessToken as it's write-only now
        dto.setApplicationName(entity.getApplicationName());
        dto.setApplicationDescription(entity.getApplicationDescription());
        dto.setProjectName(entity.getProjectName());
        dto.setProjectUrl(entity.getProjectUrl());
        dto.setTokenStatus(entity.getTokenStatus());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private PipelineExecutionDto convertPipelineExecutionToDto(PipelineExecution entity) {
        PipelineExecutionDto dto = new PipelineExecutionDto();
        dto.setId(entity.getId());
        dto.setFlowId(entity.getFlowId());
        dto.setFlowExecutionId(entity.getFlowExecutionId());
        dto.setFlowStepId(entity.getFlowStepId());

        // Get application name from FlowStep
        String applicationName = null;
        try {
            FlowStep flowStep = flowStepRepository.findById(entity.getFlowStepId()).orElse(null);
            if (flowStep != null) {
                Application application = flowStep.getApplication();
                if (application != null) {
                    applicationName = application.getApplicationName();
                }
            }
        } catch (Exception e) {
            logger.debug("Could not retrieve application name for flow step {}: {}", entity.getFlowStepId(), e.getMessage());
        }
        dto.setApplicationName(applicationName);

        dto.setPipelineId(entity.getPipelineId());
        dto.setPipelineUrl(entity.getPipelineUrl());
        // Removed jobId and jobUrl as requested - they create ambiguity for multi-job pipelines
        dto.setStartTime(entity.getStartTime());
        dto.setEndTime(entity.getEndTime());
        dto.setConfiguredTestData(entity.getConfiguredTestData());
        dto.setRuntimeTestData(entity.getRuntimeTestData());
        dto.setStatus(entity.getStatus());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setIsReplay(entity.getIsReplay());
        dto.setResumeTime(entity.getResumeTime());
        return dto;
    }
}

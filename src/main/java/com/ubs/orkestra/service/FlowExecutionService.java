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
import org.springframework.context.annotation.Lazy;
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

    @Autowired
    private FlowExecutionQueueService flowExecutionQueueService;

    @Autowired
    @Lazy
    private PipelineStatusPollingService pipelineStatusPollingService;

    @org.springframework.beans.factory.annotation.Value("${scheduling.pipeline-status.polling-interval:30000}")
    private long scheduledPollingIntervalMs;

    @org.springframework.beans.factory.annotation.Value("${flow-execution.polling-interval:5000}")
    private long flowExecutionPollingIntervalMs;

    @org.springframework.beans.factory.annotation.Value("${flow-execution.max-concurrent-flows:50}")
    private int maxConcurrentFlows;

    @Autowired(required = false)
    @Qualifier("flowExecutionTaskExecutor")
    private ThreadPoolTaskExecutor flowExecutionTaskExecutor;

    public Map<String, Object> executeMultipleFlows(String flowIdsParam) {
        return executeMultipleFlows(flowIdsParam, null, null, null, null);
    }

    public Map<String, Object> executeMultipleFlows(String flowIdsParam, String category) {
        logger.info("Processing multiple flow execution request with category: {}", category);

        final Long flowGroupId;
        final Integer iteration;
        final Integer revolutions;
        final String finalCategory;

        if (category != null && !category.trim().isEmpty()) {
            // Validate that the category exists as a FlowGroup
            String trimmedCategory = category.trim();
            FlowGroup flowGroup = flowGroupRepository.findByFlowGroupName(trimmedCategory)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid category: FlowGroup with name '" + trimmedCategory + "' not found"));

            flowGroupId = flowGroup.getId();
            iteration = flowGroup.getCurrentIteration();
            revolutions = flowGroup.getRevolutions();
            finalCategory = trimmedCategory;

            logger.info("Validated category '{}' - FlowGroup ID: {}, Current Iteration: {}, Revolutions: {}",
                       finalCategory, flowGroupId, iteration, revolutions);
        } else {
            logger.info("No category provided, using 'uncategorized' for flow executions");
            flowGroupId = null;
            iteration = null;
            revolutions = null;
            finalCategory = "uncategorized";
        }

        return executeMultipleFlows(flowIdsParam, flowGroupId, iteration, revolutions, finalCategory);
    }

    public Map<String, Object> executeMultipleFlows(String flowIdsParam, Long flowGroupId, Integer iteration, Integer revolutions, String category) {
        logger.info("Processing multiple flow execution request: {}", flowIdsParam);

        // Parse and validate flow IDs
        List<Long> flowIds = parseAndValidateFlowIds(flowIdsParam);

        // Circuit breaker: count RUNNING FlowExecutions against maxConcurrentFlows.
        // Threads are now short-lived (held only for the GitLab trigger call, ~1-5 s) so
        // thread-pool metrics are no longer the right signal for capacity.
        long runningCount = flowExecutionRepository.countByStatus(ExecutionStatus.RUNNING);
        int remainingCapacity = (int) Math.max(0, maxConcurrentFlows - runningCount);
        // Keep legacy fields so the response payload structure stays unchanged.
        int activeThreads = flowExecutionTaskExecutor != null ? flowExecutionTaskExecutor.getActiveCount() : 0;
        int maxThreads = maxConcurrentFlows;
        int queueSize = flowExecutionRepository.countByStatus(ExecutionStatus.PENDING).intValue();
        int availableCapacity = remainingCapacity;

        logger.info("Concurrent-flow circuit breaker — running: {}, maxConcurrentFlows: {}, available: {}",
                   runningCount, maxConcurrentFlows, remainingCapacity);

        // Create flow executions: flows within capacity start immediately (step-0 triggered),
        // excess flows are created as PENDING (step-0 not yet triggered).
        List<FlowExecutionDto> acceptedExecutions = new ArrayList<>();
        List<FlowExecutionDto> queuedExecutions = new ArrayList<>();

        for (int i = 0; i < flowIds.size(); i++) {
            Long flowId = flowIds.get(i);

            try {
                boolean createAsPending = remainingCapacity <= 0;
                FlowExecutionDto executionDto = createFlowExecution(
                    flowId, flowGroupId, iteration, revolutions, category, createAsPending);

                if (!createAsPending) {
                    remainingCapacity--;
                    acceptedExecutions.add(executionDto);
                    logger.info("Flow {} accepted for immediate execution with ID: {}", flowId, executionDto.getId());
                } else {
                    queuedExecutions.add(executionDto);
                    logger.info("Flow {} created as PENDING with ID: {} (max concurrent flows reached)",
                               flowId, executionDto.getId());
                }
            } catch (IllegalArgumentException e) {
                logger.error("Flow {} rejected during creation: {}", flowId, e.getMessage());
                Map<String, Object> rejectedFlow = new HashMap<>();
                rejectedFlow.put("flowId", flowId);
                rejectedFlow.put("status", "rejected");
                rejectedFlow.put("reason", "flow_not_found");
                rejectedFlow.put("message", e.getMessage());
            }
        }

        long totalQueued = flowExecutionRepository.countByStatus(ExecutionStatus.PENDING);
        
        Map<String, Object> result = new HashMap<>();
        result.put("summary", Map.of(
            "total_requested", flowIds.size(),
            "accepted", acceptedExecutions.size(),
            "queued", queuedExecutions.size(),
            "total_queued_in_system", totalQueued
        ));
        result.put("accepted", acceptedExecutions);
        result.put("queued", queuedExecutions);
        result.put("thread_pool_status", Map.of(
            "active_threads", activeThreads,
            "max_threads", maxThreads,
            "queue_size", queueSize,
            "available_capacity", availableCapacity,
            "database_queue_size", totalQueued
        ));

        logger.info("Multiple flow execution request processed - Accepted: {}, Queued: {}, Total in DB queue: {}", 
                   acceptedExecutions.size(), queuedExecutions.size(), totalQueued);
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
        return createFlowExecution(flowId, flowGroupId, iteration, revolutions, category, false);
    }

    /**
     * Creates a FlowExecution record and (when {@code createAsPending=false}) immediately triggers
     * step-0's GitLab pipeline.  When {@code createAsPending=true} the flow is parked with status
     * PENDING — step-0 is not triggered; FlowExecutionQueueService will start it later.
     */
    private FlowExecutionDto createFlowExecution(Long flowId, Long flowGroupId, Integer iteration,
                                                  Integer revolutions, String category,
                                                  boolean createAsPending) {
        logger.info("Creating flow execution for flow ID: {} (pending={})", flowId, createAsPending);

        Flow flow = flowRepository.findById(flowId)
                .orElseThrow(() -> new IllegalArgumentException("Flow not found with ID: " + flowId));

        // Create flow execution record
        FlowExecution flowExecution = new FlowExecution(flowId, new HashMap<>());
        if (createAsPending) {
            // Override the default RUNNING status — step-0 will be triggered later
            flowExecution.setStatus(ExecutionStatus.PENDING);
        }
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
                // PENDING flows: park step-0 as SCHEDULED; FlowExecutionQueueService will start it later
                if (createAsPending) {
                    placeholder.setStatus(ExecutionStatus.SCHEDULED);
                    placeholder.setStartTime(null);
                    pipelineExecutionTxService.saveNew(placeholder);
                    pipelineExecutions.add(placeholder);
                // Check if the first step has a scheduler/delay
                } else if (step.getInvokeScheduler() != null) {
                    // First step is scheduled or delayed - calculate resume time and schedule it
                    LocalDateTime previousStepEndTime = flowExecution.getStartTime() != null ? flowExecution.getStartTime() : LocalDateTime.now();
                    LocalDateTime resumeTime = schedulingService.calculateResumeTime(previousStepEndTime, step.getInvokeScheduler());

                    if (resumeTime != null) {
                        logger.info("First step {} has invokeScheduler, scheduling for execution at: {}", stepId, resumeTime);
                        placeholder.setStatus(ExecutionStatus.SCHEDULED);
                        placeholder.setResumeTime(resumeTime);
                        placeholder.setStartTime(null);
                        pipelineExecutionTxService.saveNew(placeholder);
                        pipelineExecutions.add(placeholder);
                    } else {
                        logger.warn("Failed to calculate resume time for first step {} with invokeScheduler, executing immediately", stepId);
                        PipelineExecution firstPipeline = triggerPipelineAsynchronously(flowExecution, step, placeholder);
                        pipelineExecutions.add(firstPipeline);
                    }
                } else {
                    // First step has no scheduler - trigger immediately
                    logger.info("Triggering first pipeline asynchronously for step: {}", stepId);
                    PipelineExecution firstPipeline = triggerPipelineAsynchronously(flowExecution, step, placeholder);
                    pipelineExecutions.add(firstPipeline);
                }
            } else {
                // Check if this subsequent step has a scheduler/delay
                if (step.getInvokeScheduler() != null) {
                    // For subsequent scheduled steps, we can't calculate exact resumeTime yet
                    // because we don't know when the previous step will complete.
                    // We'll set status=PENDING (not SCHEDULED) so the scheduler won't pick it up yet.
                    // executeFlowAsync will calculate the actual resumeTime when it reaches this step.
                    logger.info("Subsequent step {} has invokeScheduler, will be scheduled when execution reaches it", stepId);
                    placeholder.setStatus(ExecutionStatus.PENDING);
                    placeholder.setResumeTime(null);
                    placeholder.setStartTime(null);
                    pipelineExecutionTxService.saveNew(placeholder);
                    pipelineExecutions.add(placeholder);
                } else {
                    // Create placeholder for subsequent steps without scheduler
                    placeholder.setStatus(ExecutionStatus.SCHEDULED);
                    placeholder.setStartTime(null);
                    pipelineExecutionTxService.saveNew(placeholder);
                    pipelineExecutions.add(placeholder);
                }
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

                // Register for polling — mock completion is detected by elapsed-time check in PipelineStatusPollingService
                pipelineStatusPollingService.registerPipelineForPolling(pipelineExecution);

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

                    // Register for polling — PipelineStatusPollingService drives advancement when done
                    pipelineStatusPollingService.registerPipelineForPolling(pipelineExecution);

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

    // DEPRECATED: Replaced by PipelineStatusPollingService scheduled polling
    // Kept for backward compatibility but not actively used
    @Deprecated
    @Async("pipelinePollingTaskExecutor")
    public void pollPipelineCompletionAsync(PipelineExecution pipelineExecution, Application application, String gitlabBaseUrl, FlowStep step) {
        logger.warn("DEPRECATED: Using old pollPipelineCompletionAsync method. Should use PipelineStatusPollingService instead.");
        
        // Delegate to new polling service
        pipelineStatusPollingService.registerPipelineForPolling(pipelineExecution);
    }

    /**
     * Fire-and-poll architecture: this method no longer drives the step-by-step loop.
     * Step-0 is already triggered by {@link #createFlowExecution} and registered for polling.
     * Subsequent steps are driven by {@link #advanceFlowToNextStep}, which is called by
     * {@link PipelineStatusPollingService#triggerFlowContinuation} whenever a pipeline completes.
     *
     * <p>This method's only remaining jobs are:
     * <ul>
     *   <li>Starting PENDING flows (called from FlowExecutionQueueService when capacity opens)</li>
     *   <li>Re-registering in-flight pipelines for polling after a service restart (recovery)</li>
     * </ul>
     */
    @Async("flowExecutionTaskExecutor")
    public CompletableFuture<FlowExecutionDto> executeFlowAsync(UUID flowExecutionId) {
        logger.info("executeFlowAsync: ensuring flow execution {} is tracked (fire-and-poll)", flowExecutionId);
        org.slf4j.MDC.put("flowExecutionId", flowExecutionId.toString());
        try {
            FlowExecution flowExecution = flowExecutionRepository.findById(flowExecutionId).orElse(null);
            if (flowExecution == null) {
                logger.warn("executeFlowAsync: flow execution {} not found", flowExecutionId);
                return CompletableFuture.completedFuture(null);
            }

            // If PENDING, start it now (capacity opened up)
            if (flowExecution.getStatus() == ExecutionStatus.PENDING) {
                logger.info("executeFlowAsync: starting PENDING flow execution {}", flowExecutionId);
                startPendingFlowExecution(flowExecution);
                return CompletableFuture.completedFuture(convertToDto(flowExecution));
            }

            // Already in terminal state — nothing to do
            if (flowExecution.getStatus() == ExecutionStatus.PASSED
                    || flowExecution.getStatus() == ExecutionStatus.FAILED
                    || flowExecution.getStatus() == ExecutionStatus.CANCELLED) {
                return CompletableFuture.completedFuture(convertToDto(flowExecution));
            }

            // RUNNING flow: find any pipelines that are RUNNING and ensure they are registered
            // for polling. Covers the restart-recovery case where polling context was lost.
            pipelineExecutionRepository.findByFlowExecutionIdOrderByCreatedAt(flowExecutionId).stream()
                .filter(pe -> pe.getStatus() == ExecutionStatus.RUNNING)
                .forEach(pe -> {
                    logger.info("executeFlowAsync: re-registering in-flight pipeline {} for polling (recovery)", pe.getId());
                    pipelineStatusPollingService.registerPipelineForPolling(pe);
                });

            return CompletableFuture.completedFuture(convertToDto(flowExecution));
        } catch (Exception e) {
            logger.error("executeFlowAsync error for {}: {}", flowExecutionId, e.getMessage(), e);
            throw new RuntimeException(e);
        } finally {
            org.slf4j.MDC.remove("flowExecutionId");
        }
    }

    /**
     * Transitions a PENDING FlowExecution to RUNNING and triggers its step-0 pipeline.
     */
    private void startPendingFlowExecution(FlowExecution flowExecution) {
        try {
            Flow flow = flowRepository.findById(flowExecution.getFlowId()).orElse(null);
            if (flow == null || flow.getFlowStepIds().isEmpty()) {
                flowExecution.setStatus(ExecutionStatus.FAILED);
                flowExecution.setEndTime(LocalDateTime.now());
                flowExecutionRepository.save(flowExecution);
                return;
            }

            flowExecution.setStatus(ExecutionStatus.RUNNING);
            flowExecution = flowExecutionRepository.save(flowExecution);

            Long step0Id = flow.getFlowStepIds().get(0);
            FlowStep step0 = flowStepRepository.findById(step0Id).orElse(null);
            if (step0 == null) {
                flowExecution.setStatus(ExecutionStatus.FAILED);
                flowExecution.setEndTime(LocalDateTime.now());
                flowExecutionRepository.save(flowExecution);
                return;
            }

            // Check for invokeScheduler on step-0
            if (step0.getInvokeScheduler() != null) {
                LocalDateTime base = flowExecution.getStartTime() != null ? flowExecution.getStartTime() : LocalDateTime.now();
                LocalDateTime resumeTime = schedulingService.calculateResumeTime(base, step0.getInvokeScheduler());
                if (resumeTime != null) {
                    PipelineExecution pe = pipelineExecutionRepository
                        .findByFlowExecutionIdAndFlowStepId(flowExecution.getId(), step0Id).orElse(null);
                    if (pe != null) {
                        schedulingService.schedulePipelineExecution(pe, resumeTime);
                    }
                    return;
                }
            }

            Map<String, String> pipelineVars = new HashMap<>(testDataService.mergeTestDataByIds(step0.getTestDataIds()));
            triggerAndRegisterStep(flowExecution, step0, pipelineVars);

        } catch (Exception e) {
            logger.error("startPendingFlowExecution error for {}: {}", flowExecution.getId(), e.getMessage(), e);
            flowExecution.setStatus(ExecutionStatus.FAILED);
            flowExecution.setEndTime(LocalDateTime.now());
            flowExecutionRepository.save(flowExecution);
        }
    }

    /**
     * State-machine driver called by {@link PipelineStatusPollingService#triggerFlowContinuation}
     * whenever a pipeline finishes.  Accumulates runtime variables, determines the next step,
     * and either triggers it or marks the flow complete.
     *
     * @param flowExecutionId the parent flow execution
     * @param completedStepId the FlowStep whose pipeline just finished
     */
    @Async("flowExecutionTaskExecutor")
    @Transactional
    public void advanceFlowToNextStep(UUID flowExecutionId, Long completedStepId) {
        logger.info("advanceFlowToNextStep: flow={} completedStep={}", flowExecutionId, completedStepId);
        org.slf4j.MDC.put("flowExecutionId", flowExecutionId.toString());
        try {
            FlowExecution flowExecution = flowExecutionRepository.findById(flowExecutionId).orElse(null);
            if (flowExecution == null) {
                logger.error("advanceFlowToNextStep: flow execution {} not found", flowExecutionId);
                return;
            }
            // Guard: don't advance a flow that already reached a terminal state
            if (flowExecution.getStatus() == ExecutionStatus.PASSED
                    || flowExecution.getStatus() == ExecutionStatus.FAILED
                    || flowExecution.getStatus() == ExecutionStatus.CANCELLED) {
                logger.debug("advanceFlowToNextStep: flow {} already in terminal state {}", flowExecutionId, flowExecution.getStatus());
                return;
            }

            Flow flow = flowRepository.findById(flowExecution.getFlowId()).orElse(null);
            if (flow == null) {
                logger.error("advanceFlowToNextStep: flow {} not found", flowExecution.getFlowId());
                return;
            }

            // Retrieve the completed pipeline execution
            PipelineExecution completedPipeline = pipelineExecutionRepository
                .findByFlowExecutionIdAndFlowStepId(flowExecutionId, completedStepId).orElse(null);

            // Merge accumulated runtime variables: FlowExecution base + completed step output
            Map<String, String> accumulatedVars = new HashMap<>(
                flowExecution.getRuntimeVariables() != null ? flowExecution.getRuntimeVariables() : Collections.emptyMap());
            if (completedPipeline != null && completedPipeline.getRuntimeTestData() != null) {
                accumulatedVars.putAll(completedPipeline.getRuntimeTestData());
            }

            // If the completed step failed, mark the whole flow as failed
            if (completedPipeline != null && completedPipeline.getStatus() == ExecutionStatus.FAILED) {
                flowExecution.setStatus(ExecutionStatus.FAILED);
                flowExecution.setEndTime(LocalDateTime.now());
                flowExecution.setRuntimeVariables(accumulatedVars);
                flowExecutionRepository.save(flowExecution);
                cancelRemainingPipelineExecutions(flowExecutionId, completedStepId);
                logger.error("advanceFlowToNextStep: flow {} FAILED at step {}", flowExecutionId, completedStepId);
                return;
            }

            // Persist accumulated variables before advancing
            flowExecution.setRuntimeVariables(accumulatedVars);
            flowExecutionRepository.save(flowExecution);

            List<Long> stepIds = flow.getFlowStepIds();
            int completedIndex = stepIds.indexOf(completedStepId);
            LocalDateTime previousStepEndTime = (completedPipeline != null && completedPipeline.getEndTime() != null)
                ? completedPipeline.getEndTime() : LocalDateTime.now();

            // Check if this was the last step
            if (completedIndex < 0 || completedIndex + 1 >= stepIds.size()) {
                flowExecution.setStatus(ExecutionStatus.PASSED);
                flowExecution.setEndTime(LocalDateTime.now());
                flowExecutionRepository.save(flowExecution);
                logger.info("advanceFlowToNextStep: flow {} PASSED after {} steps", flowExecutionId, stepIds.size());
                return;
            }

            // Find the next step
            Long nextStepId = stepIds.get(completedIndex + 1);
            FlowStep nextStep = flowStepRepository.findById(nextStepId).orElse(null);
            if (nextStep == null) {
                logger.error("advanceFlowToNextStep: next step {} not found", nextStepId);
                flowExecution.setStatus(ExecutionStatus.FAILED);
                flowExecution.setEndTime(LocalDateTime.now());
                flowExecutionRepository.save(flowExecution);
                return;
            }

            // Check for invokeScheduler on the next step
            if (nextStep.getInvokeScheduler() != null) {
                LocalDateTime resumeTime = schedulingService.calculateResumeTime(previousStepEndTime, nextStep.getInvokeScheduler());
                if (resumeTime != null) {
                    PipelineExecution nextPipeline = pipelineExecutionRepository
                        .findByFlowExecutionIdAndFlowStepId(flowExecutionId, nextStepId).orElse(null);
                    if (nextPipeline != null) {
                        // Persist accumulated variables so resumeFlowExecution can read them
                        nextPipeline.setRuntimeTestData(new HashMap<>(accumulatedVars));
                        schedulingService.schedulePipelineExecution(nextPipeline, resumeTime);
                    }
                    logger.info("advanceFlowToNextStep: flow {} paused at scheduled step {} (resume at {})",
                               flowExecutionId, nextStepId, resumeTime);
                    return;
                }
            }

            // Trigger the next step pipeline immediately
            Map<String, String> pipelineVars = new HashMap<>(testDataService.mergeTestDataByIds(nextStep.getTestDataIds()));
            pipelineVars.putAll(accumulatedVars);
            triggerAndRegisterStep(flowExecution, nextStep, pipelineVars);

        } catch (Exception e) {
            logger.error("advanceFlowToNextStep error for flow {}: {}", flowExecutionId, e.getMessage(), e);
            try {
                FlowExecution fe = flowExecutionRepository.findById(flowExecutionId).orElse(null);
                if (fe != null && fe.getStatus() == ExecutionStatus.RUNNING) {
                    fe.setStatus(ExecutionStatus.FAILED);
                    fe.setEndTime(LocalDateTime.now());
                    flowExecutionRepository.save(fe);
                }
            } catch (Exception ex) {
                logger.error("Failed to mark flow {} as FAILED after advanceFlowToNextStep error", flowExecutionId);
            }
        } finally {
            org.slf4j.MDC.remove("flowExecutionId");
        }
    }

    /**
     * Triggers the GitLab pipeline for a flow step and registers it for polling.
     * This is the "fire" half of the fire-and-poll architecture.
     * Thread held only for the duration of the trigger API call (~1-5 s).
     */
    private void triggerAndRegisterStep(FlowExecution flowExecution, FlowStep step, Map<String, String> pipelineVars) {
        logger.info("triggerAndRegisterStep: flow={} step={}", flowExecution.getId(), step.getId());

        PipelineExecution pe = pipelineExecutionRepository
            .findByFlowExecutionIdAndFlowStepId(flowExecution.getId(), step.getId()).orElse(null);
        if (pe == null) {
            logger.error("triggerAndRegisterStep: pipeline execution record not found for flow {} step {}",
                        flowExecution.getId(), step.getId());
            return;
        }

        Map<String, String> mergedVars = new HashMap<>(pipelineVars);
        if (step.getTestTag() != null && !step.getTestTag().trim().isEmpty()) {
            mergedVars.put("testTag", step.getTestTag());
        }
        mergedVars.put("EXECUTION_UUID", flowExecution.getId().toString());
        mergedVars.put("APP_NAME", step.getApplication().getApplicationName());

        pe.setStatus(ExecutionStatus.RUNNING);
        pe.setStartTime(LocalDateTime.now());
        pe = pipelineExecutionRepository.save(pe);

        if (gitLabConfig.isMockMode()) {
            long mockId = System.currentTimeMillis();
            pe.setPipelineId(mockId);
            pe.setPipelineUrl("https://gitlab.com/" + step.getApplication().getGitlabProjectId() + "/-/pipelines/" + mockId);
            pe = pipelineExecutionRepository.save(pe);
            pipelineStatusPollingService.registerPipelineForPolling(pe);
            logger.info("MOCK: pipeline triggered for step {}: id={}", step.getId(), mockId);
        } else {
            try {
                GitLabApiClient.GitLabPipelineResponse response = gitLabApiClient
                    .triggerPipeline(gitLabConfig.getBaseUrl(), step.getApplication().getGitlabProjectId(),
                        step.getBranch(),
                        applicationService.getDecryptedPersonalAccessToken(step.getApplication().getId()),
                        mergedVars)
                    .block();

                if (response != null) {
                    pe.setPipelineId(response.getId());
                    pe.setPipelineUrl(response.getWebUrl());
                    pe = pipelineExecutionRepository.save(pe);
                    pipelineStatusPollingService.registerPipelineForPolling(pe);
                    logger.info("Pipeline triggered for step {}: id={}", step.getId(), response.getId());
                } else {
                    logger.error("triggerAndRegisterStep: null response for step {}", step.getId());
                    pe.setStatus(ExecutionStatus.FAILED);
                    pe.setEndTime(LocalDateTime.now());
                    pipelineExecutionRepository.save(pe);
                    flowExecution.setStatus(ExecutionStatus.FAILED);
                    flowExecution.setEndTime(LocalDateTime.now());
                    flowExecutionRepository.save(flowExecution);
                    cancelRemainingPipelineExecutions(flowExecution.getId(), step.getId());
                }
            } catch (Exception e) {
                logger.error("triggerAndRegisterStep: error triggering pipeline for step {}: {}", step.getId(), e.getMessage(), e);
                pe.setStatus(ExecutionStatus.FAILED);
                pe.setEndTime(LocalDateTime.now());
                pipelineExecutionRepository.save(pe);
                flowExecution.setStatus(ExecutionStatus.FAILED);
                flowExecution.setEndTime(LocalDateTime.now());
                flowExecutionRepository.save(flowExecution);
                cancelRemainingPipelineExecutions(flowExecution.getId(), step.getId());
            }
        }
    }

    public FlowExecutionDto createReplayFlowExecution(UUID originalFlowExecutionId, Long failedFlowStepId) {
        logger.info("Creating replay flow execution for original execution: {} from failed step: {}", originalFlowExecutionId, failedFlowStepId);

        FlowExecution originalExecution = flowExecutionRepository.findById(originalFlowExecutionId)
                .orElseThrow(() -> new IllegalArgumentException("Original flow execution not found with ID: " + originalFlowExecutionId));

        if (originalExecution.getStatus() != ExecutionStatus.FAILED) {
            throw new IllegalArgumentException("Can only replay failed flow executions");
        }

        final Long flowId = originalExecution.getFlowId();
        Flow flow = flowRepository.findById(flowId)
                .orElseThrow(() -> new IllegalArgumentException("Flow not found with ID: " + flowId));

        // Validate that the failed step exists in the flow
        if (!flow.getFlowStepIds().contains(failedFlowStepId)) {
            throw new IllegalArgumentException("Failed flow step ID " + failedFlowStepId + " is not part of flow " + flow.getId());
        }

        // Get all successful pipeline executions up to the failed step to extract runtime variables
        Map<String, String> accumulatedRuntimeVariables = extractRuntimeVariablesUpToStep(originalFlowExecutionId, failedFlowStepId, flow);

        // CHANGED: Reuse the same flow execution instead of creating a new one
        // Increment replay count
        Integer currentReplayCount = originalExecution.getReplayCount();
        if (currentReplayCount == null) {
            currentReplayCount = 0;
        }
        originalExecution.setReplayCount(currentReplayCount + 1);
        
        // Reset the execution for replay
        originalExecution.setStatus(ExecutionStatus.RUNNING);
        originalExecution.setStartTime(LocalDateTime.now());
        originalExecution.setEndTime(null);
        originalExecution.setRuntimeVariables(accumulatedRuntimeVariables);
        originalExecution = flowExecutionRepository.save(originalExecution);

        // Build a map of original successful pipelines before the failed step (by flowStepId)
        int failedStepIndex = flow.getFlowStepIds().indexOf(failedFlowStepId);
        List<PipelineExecution> originalPipelinesOrdered = pipelineExecutionRepository
                .findByFlowExecutionIdOrderByCreatedAt(originalFlowExecutionId);
        Map<Long, PipelineExecution> originalPassedByStep = originalPipelinesOrdered.stream()
                .filter(pe -> pe.getStatus() == ExecutionStatus.PASSED)
                .collect(Collectors.toMap(PipelineExecution::getFlowStepId, pe -> pe, (a, b) -> a));

        // Delete all existing pipeline executions for this flow execution to recreate them
        pipelineExecutionRepository.deleteByFlowExecutionId(originalFlowExecutionId);

        // 1) Pre-create "carried" entries for steps BEFORE the failed step, mark PASSED and reference original pipeline/job
        for (int i = 0; i < failedStepIndex; i++) {
            Long stepId = flow.getFlowStepIds().get(i);
            FlowStep step = flowStepRepository.findById(stepId)
                    .orElseThrow(() -> new IllegalArgumentException("Flow step not found with ID: " + stepId));

            PipelineExecution originalPe = originalPassedByStep.get(stepId);
            PipelineExecution carried = new PipelineExecution();
            carried.setFlowId(originalExecution.getFlowId());
            carried.setFlowExecutionId(originalExecution.getId());
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
            placeholder.setFlowId(originalExecution.getFlowId());
            placeholder.setFlowExecutionId(originalExecution.getId());
            placeholder.setFlowStepId(stepId);
            placeholder.setConfiguredTestData(testDataService.mergeTestDataByIds(step.getTestDataIds()));
            // Seed with accumulated variables present at replay start
            placeholder.setRuntimeTestData(new HashMap<>(accumulatedRuntimeVariables));
            placeholder.setStatus(ExecutionStatus.SCHEDULED);
            placeholder.setStartTime(null);
            placeholder.setIsReplay(true);
            pipelineExecutionTxService.saveNew(placeholder);
        }

        logger.info("Updated flow execution with ID: {} (replayCount: {}) to replay from failed step: {}", originalExecution.getId(), originalExecution.getReplayCount(), failedFlowStepId);
        return convertToDto(originalExecution);
    }

    /**
     * Called by the scheduler when a scheduled/delayed step's time arrives.
     * Triggers the step's GitLab pipeline and registers it for polling.
     * {@link #advanceFlowToNextStep} handles continuation after it completes.
     */
    @Async("flowExecutionTaskExecutor")
    public void resumeFlowExecution(UUID flowExecutionId, Long scheduledStepId) {
        logger.info("resumeFlowExecution: flow={} scheduledStep={}", flowExecutionId, scheduledStepId);
        org.slf4j.MDC.put("flowExecutionId", flowExecutionId.toString());
        try {
            FlowExecution flowExecution = flowExecutionRepository.findById(flowExecutionId).orElse(null);
            if (flowExecution == null) {
                logger.error("resumeFlowExecution: flow execution {} not found", flowExecutionId);
                return;
            }
            if (flowExecution.getStatus() != ExecutionStatus.RUNNING) {
                logger.warn("resumeFlowExecution: flow {} is not RUNNING (status={}), skipping",
                           flowExecutionId, flowExecution.getStatus());
                return;
            }

            FlowStep step = flowStepRepository.findById(scheduledStepId).orElse(null);
            if (step == null) {
                logger.error("resumeFlowExecution: step {} not found", scheduledStepId);
                return;
            }

            // Variables: step test data + accumulated flow runtime + any runtime already on the pipeline record
            Map<String, String> pipelineVars = new HashMap<>(testDataService.mergeTestDataByIds(step.getTestDataIds()));
            if (flowExecution.getRuntimeVariables() != null) {
                pipelineVars.putAll(flowExecution.getRuntimeVariables());
            }
            PipelineExecution existingPe = pipelineExecutionRepository
                .findByFlowExecutionIdAndFlowStepId(flowExecutionId, scheduledStepId).orElse(null);
            if (existingPe != null && existingPe.getRuntimeTestData() != null) {
                pipelineVars.putAll(existingPe.getRuntimeTestData());
            }

            // Fire the pipeline — PipelineStatusPollingService drives advancement on completion
            triggerAndRegisterStep(flowExecution, step, pipelineVars);

        } catch (Exception e) {
            logger.error("resumeFlowExecution error for flow {}: {}", flowExecutionId, e.getMessage(), e);
            try {
                FlowExecution fe = flowExecutionRepository.findById(flowExecutionId).orElse(null);
                if (fe != null) {
                    fe.setStatus(ExecutionStatus.FAILED);
                    fe.setEndTime(LocalDateTime.now());
                    flowExecutionRepository.save(fe);
                }
            } catch (Exception ex) {
                logger.error("Failed to mark flow {} as FAILED after resumeFlowExecution error", flowExecutionId);
            }
        } finally {
            org.slf4j.MDC.remove("flowExecutionId");
        }
    }

    /**
     * Triggers the first replay step and registers it for polling.
     * {@link #advanceFlowToNextStep} handles all subsequent steps when each pipeline completes.
     */
    @Async("flowExecutionTaskExecutor")
    public CompletableFuture<FlowExecutionDto> executeReplayFlowAsync(UUID replayFlowExecutionId, UUID originalFlowExecutionId, Long failedFlowStepId) {
        logger.info("executeReplayFlowAsync: replay={} fromStep={}", replayFlowExecutionId, failedFlowStepId);
        org.slf4j.MDC.put("flowExecutionId", replayFlowExecutionId.toString());
        try {
            FlowExecution replayExecution = flowExecutionRepository.findById(replayFlowExecutionId).orElse(null);
            if (replayExecution == null) {
                logger.error("executeReplayFlowAsync: replay execution {} not found", replayFlowExecutionId);
                return CompletableFuture.completedFuture(null);
            }

            FlowStep failedStep = flowStepRepository.findById(failedFlowStepId).orElse(null);
            if (failedStep == null) {
                logger.error("executeReplayFlowAsync: failed step {} not found", failedFlowStepId);
                replayExecution.setStatus(ExecutionStatus.FAILED);
                replayExecution.setEndTime(LocalDateTime.now());
                flowExecutionRepository.save(replayExecution);
                return CompletableFuture.completedFuture(convertToDto(replayExecution));
            }

            // Variables: step test data + runtime accumulated from successful steps before the failed one
            Map<String, String> pipelineVars = new HashMap<>(testDataService.mergeTestDataByIds(failedStep.getTestDataIds()));
            if (replayExecution.getRuntimeVariables() != null) {
                pipelineVars.putAll(replayExecution.getRuntimeVariables());
            }

            // Fire the first replay step — advanceFlowToNextStep handles the rest
            triggerAndRegisterStep(replayExecution, failedStep, pipelineVars);

            return CompletableFuture.completedFuture(convertToDto(replayExecution));
        } catch (Exception e) {
            logger.error("executeReplayFlowAsync error for {}: {}", replayFlowExecutionId, e.getMessage(), e);
            try {
                FlowExecution fe = flowExecutionRepository.findById(replayFlowExecutionId).orElse(null);
                if (fe != null) {
                    fe.setStatus(ExecutionStatus.FAILED);
                    fe.setEndTime(LocalDateTime.now());
                    flowExecutionRepository.save(fe);
                }
            } catch (Exception ex) {
                logger.error("Failed to mark replay flow {} as FAILED", replayFlowExecutionId);
            }
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

                // Register for polling — mock completion handled by PipelineStatusPollingService
                pipelineStatusPollingService.registerPipelineForPolling(pipelineExecution);

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

                    // Register for polling — advancement driven by PipelineStatusPollingService
                    pipelineStatusPollingService.registerPipelineForPolling(pipelineExecution);
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

                // Register for polling — mock completion is detected by elapsed-time check
                pipelineStatusPollingService.registerPipelineForPolling(pipelineExecution);

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

                    // Register for polling — PipelineStatusPollingService drives advancement on completion
                    pipelineStatusPollingService.registerPipelineForPolling(pipelineExecution);
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

    // Made package-private to allow PipelineStatusPollingService to access it
  void downloadAndParseArtifacts(PipelineExecution pipelineExecution, Application application, 
                                         String gitlabBaseUrl, FlowStep step) {
        try {
            // Get pipeline jobs
            GitLabApiClient.GitLabJobsResponse[] jobs = gitLabApiClient
                    .getPipelineJobs(gitlabBaseUrl, application.getGitlabProjectId(),
                                   pipelineExecution.getPipelineId(), applicationService.getDecryptedPersonalAccessToken(application.getId()))
                    .block();
            
            if (jobs != null && jobs.length > 0) {
                // Find job for the specified test stage (regardless of success/failure)
                // This allows us to parse output.env even from failed steps
                GitLabApiClient.GitLabJobsResponse targetJob = null;
                for (GitLabApiClient.GitLabJobsResponse job : jobs) {
                    if (step.getTestStage().equals(job.getStage())) {
                        // If we find a job in this stage, use it (prefer the last one if multiple exist)
                        targetJob = job;
                        // Don't break - continue to find the last job in this stage
                    }
                }
                
                if (targetJob != null) {
                    logger.info("Found target job {} (status: {}) in stage {} for pipeline {}",
                               targetJob.getId(), targetJob.getStatus(), targetJob.getStage(), pipelineExecution.getPipelineId());

                    // Set job information
                    pipelineExecution.setJobId(targetJob.getId());
                    pipelineExecution.setJobUrl(targetJob.getWebUrl());
                    pipelineExecutionRepository.save(pipelineExecution);

                    // Download output.env from target/output.env (even if job failed)
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
                        logger.info("Successfully downloaded and parsed artifacts from job {} (status: {}): {} variables (total runtime: {})",
                                   targetJob.getId(), targetJob.getStatus(), parsedVariables.size(), runtimeTestData.size());
                    } else {
                        logger.info("No artifact content found in job {}, runtime data will remain as configured data",
                                   targetJob.getId());
                        // Set runtime data to configured data if no artifacts
                        pipelineExecution.setRuntimeTestData(pipelineExecution.getConfiguredTestData());
                        pipelineExecutionRepository.save(pipelineExecution);
                    }
                } else {
                    logger.info("No job found for stage '{}' in pipeline {}",
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
     * Cancel all remaining SCHEDULED or PENDING pipeline executions when a flow fails.
     * This ensures UI shows correct status - no ambiguous SCHEDULED status when flow is FAILED.
     */
    private void cancelRemainingPipelineExecutions(UUID flowExecutionId, Long failedAtStepId) {
        logger.info("Cancelling remaining pipeline executions for flow execution {} after failure at step {}", 
                   flowExecutionId, failedAtStepId);
        
        try {
            // Get all pipeline executions for this flow execution
            List<PipelineExecution> allPipelineExecutions = pipelineExecutionRepository.findByFlowExecutionIdOrderByCreatedAt(flowExecutionId);
            
            // Find all SCHEDULED or PENDING executions (not yet started)
            List<PipelineExecution> remainingExecutions = allPipelineExecutions.stream()
                .filter(pe -> pe.getStatus() == ExecutionStatus.SCHEDULED || pe.getStatus() == ExecutionStatus.PENDING)
                .filter(pe -> !pe.getFlowStepId().equals(failedAtStepId)) // Don't cancel the failed step itself
                .collect(Collectors.toList());
            
            if (!remainingExecutions.isEmpty()) {
                logger.info("Found {} remaining SCHEDULED/PENDING pipeline executions to cancel", remainingExecutions.size());
                
                for (PipelineExecution pe : remainingExecutions) {
                    pe.setStatus(ExecutionStatus.CANCELLED);
                    pe.setEndTime(LocalDateTime.now());
                    pipelineExecutionRepository.save(pe);
                    logger.debug("Cancelled pipeline execution for step {}", pe.getFlowStepId());
                }
                
                logger.info("Successfully cancelled {} remaining pipeline executions", remainingExecutions.size());
            } else {
                logger.debug("No remaining SCHEDULED/PENDING pipeline executions to cancel");
            }
        } catch (Exception e) {
            logger.error("Error cancelling remaining pipeline executions: {}", e.getMessage(), e);
            // Don't throw - this is cleanup, shouldn't affect main flow
        }
    }

    /**
     * Wait for a pipeline that is already running to complete
     */
    // DEPRECATED: Replaced by PipelineStatusPollingService.waitForPipelineCompletion
    // Kept for backward compatibility but not actively used
    @Deprecated
    private PipelineExecution waitForPipelineCompletion(PipelineExecution pipelineExecution, Application application, FlowStep step) {
        logger.warn("DEPRECATED: Using old waitForPipelineCompletion method. Should use PipelineStatusPollingService instead.");
        
        // Delegate to new polling service
        pipelineStatusPollingService.registerPipelineForPolling(pipelineExecution);
        return pipelineStatusPollingService.waitForPipelineCompletion(pipelineExecution, 3600000);
    }

    // DEPRECATED: Replaced by PipelineStatusPollingService scheduled polling
    // Kept for backward compatibility but not actively used
    @Deprecated
    @Async("pipelinePollingTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void pollPipelineCompletion(PipelineExecution pipelineExecution, Application application, String gitlabBaseUrl, FlowStep step) {
        logger.warn("DEPRECATED: Using old pollPipelineCompletion method. Should use PipelineStatusPollingService instead.");
        
        // Delegate to new polling service
        pipelineStatusPollingService.registerPipelineForPolling(pipelineExecution);
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
        dto.setReplayCount(entity.getReplayCount());
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
     * 
     * CRITICAL FIX: Only use pipeline executions that belong to THIS specific flowExecutionId.
     * Previously, the map merge logic could pick pipeline executions from other executions of the same flow,
     * causing status display issues in the UI (e.g., showing old FAILED status as SCHEDULED in new executions).
     */
    private List<PipelineExecutionDto> getAllPipelineExecutionsForFlowExecution(UUID flowExecutionId, Flow flow, List<FlowStep> flowSteps) {
        // Get existing pipeline executions for THIS SPECIFIC flow execution - ensure fresh data
        // This query already filters by flowExecutionId, so we only get executions for this specific run
        List<PipelineExecution> existingPipelineExecutions = pipelineExecutionRepository.findByFlowExecutionIdOrderByCreatedAt(flowExecutionId);

        // Create a map of existing executions by flowStepId for quick lookup
        // Since we're already filtered by flowExecutionId, there should be no duplicates per stepId
        // But we keep the merge function for safety - in case of duplicates (e.g., retries), take the latest (b)
        Map<Long, PipelineExecution> existingByStepId = existingPipelineExecutions.stream()
                .collect(Collectors.toMap(
                    PipelineExecution::getFlowStepId, 
                    pe -> pe, 
                    (a, b) -> {
                        // If duplicates exist for same step (shouldn't happen normally), take the latest one
                        logger.warn("Found duplicate pipeline executions for flowExecutionId {} and flowStepId {}. Taking latest.", flowExecutionId, b.getFlowStepId());
                        return b.getCreatedAt().isAfter(a.getCreatedAt()) ? b : a;
                    }
                ));

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
                 // No database record yet
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

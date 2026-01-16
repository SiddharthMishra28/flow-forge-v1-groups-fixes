package com.ubs.orkestra.service;

import com.ubs.orkestra.dto.*;
import com.ubs.orkestra.model.Flow;
import com.ubs.orkestra.model.FlowStep;
import com.ubs.orkestra.model.InvokeScheduler;
import com.ubs.orkestra.model.Timer;
import com.ubs.orkestra.repository.ApplicationRepository;
import com.ubs.orkestra.repository.FlowRepository;
import com.ubs.orkestra.repository.FlowStepRepository;
import com.ubs.orkestra.repository.TestDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class CombinedFlowService {

    private static final Logger logger = LoggerFactory.getLogger(CombinedFlowService.class);

    @Autowired
    private FlowRepository flowRepository;

    @Autowired
    private FlowStepRepository flowStepRepository;

    @Autowired
    private TestDataRepository testDataRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private TestDataService testDataService;

    public CombinedFlowDto createFlowFromCreateDto(FlowCreateDto flowCreateDto) {
        logger.info("Creating new flow from create DTO with {} steps", flowCreateDto.getFlowSteps().size());
        
        // Validate all applications exist
        validateApplicationsExistFromCreateDto(flowCreateDto.getFlowSteps());
        
        // Validate all test data IDs exist
        validateTestDataExist(flowCreateDto.getFlowSteps());
        
        // Create flow steps
        List<Long> flowStepIds = new ArrayList<>();
        List<FlowStep> savedFlowSteps = new ArrayList<>();
        for (FlowStepCreateDto stepDto : flowCreateDto.getFlowSteps()) {
            // Create flow step
            FlowStep flowStep = new FlowStep();
            flowStep.setApplicationId(stepDto.getApplicationId());
            flowStep.setBranch(stepDto.getBranch());
            flowStep.setTestTag(stepDto.getTestTag());
            flowStep.setTestStage(stepDto.getTestStage());
            flowStep.setDescription(stepDto.getDescription());
            flowStep.setSquashStepIds(stepDto.getSquashStepIds());
            flowStep.setTestDataIds(stepDto.getTestData() != null ? stepDto.getTestData() : new ArrayList<>());
            flowStep.setInvokeScheduler(convertInvokeSchedulerDtoToEntity(stepDto.getInvokeScheduler()));
            
            FlowStep savedFlowStep = flowStepRepository.save(flowStep);
            savedFlowSteps.add(savedFlowStep);
            flowStepIds.add(savedFlowStep.getId());
        }
        
        // Create flow
        Flow flow = new Flow();
        flow.setFlowStepIds(flowStepIds);
        flow.setSquashTestCaseId(flowCreateDto.getSquashTestCaseId());
        flow.setSquashTestCase(flowCreateDto.getSquashTestCase());
        
        Flow savedFlow = flowRepository.save(flow);
        
        logger.info("Flow created with ID: {}", savedFlow.getId());
        return convertToDto(savedFlow, savedFlowSteps);
    }

    public CombinedFlowDto updateFlowFromCreateDto(Long id, FlowCreateDto flowCreateDto) {
        logger.info("Updating flow with ID: {} from create DTO", id);
        
        Flow existingFlow = flowRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Flow not found with ID: " + id));
        
        // Validate all applications exist
        validateApplicationsExistFromCreateDto(flowCreateDto.getFlowSteps());
        
        // Validate all test data IDs exist
        validateTestDataExist(flowCreateDto.getFlowSteps());
        
        // Delete old flow steps but DON'T delete test data (just unlink)
        List<FlowStep> oldFlowSteps = flowStepRepository.findByIdIn(existingFlow.getFlowStepIds());
        unlinkFlowStepsAndTestData(oldFlowSteps);
        
        // Create new flow steps
        List<Long> newFlowStepIds = new ArrayList<>();
        List<FlowStep> newFlowSteps = new ArrayList<>();
        
        for (FlowStepCreateDto stepDto : flowCreateDto.getFlowSteps()) {
            // Create flow step
            FlowStep flowStep = new FlowStep();
            flowStep.setApplicationId(stepDto.getApplicationId());
            flowStep.setBranch(stepDto.getBranch());
            flowStep.setTestTag(stepDto.getTestTag());
            flowStep.setTestStage(stepDto.getTestStage());
            flowStep.setDescription(stepDto.getDescription());
            flowStep.setSquashStepIds(stepDto.getSquashStepIds());
            flowStep.setTestDataIds(stepDto.getTestData() != null ? stepDto.getTestData() : new ArrayList<>());
            flowStep.setInvokeScheduler(convertInvokeSchedulerDtoToEntity(stepDto.getInvokeScheduler()));
            
            FlowStep savedFlowStep = flowStepRepository.save(flowStep);
            newFlowSteps.add(savedFlowStep);
            newFlowStepIds.add(savedFlowStep.getId());
        }
        
        // Update flow
        existingFlow.setFlowStepIds(newFlowStepIds);
        existingFlow.setSquashTestCaseId(flowCreateDto.getSquashTestCaseId());
        existingFlow.setSquashTestCase(flowCreateDto.getSquashTestCase());
        
        Flow updatedFlow = flowRepository.save(existingFlow);
        
        logger.info("Flow updated successfully with ID: {}", updatedFlow.getId());
        return convertToDto(updatedFlow, newFlowSteps);
    }

    public CombinedFlowDto createCombinedFlow(CombinedFlowDto combinedFlowDto) {
        logger.info("Creating new combined flow with {} steps", combinedFlowDto.getFlowSteps().size());
        
        // Validate all applications exist
        validateApplicationsExist(combinedFlowDto.getFlowSteps());
        
        // Create and save flow steps with test data
        List<Long> flowStepIds = new ArrayList<>();
        List<FlowStep> savedFlowSteps = new ArrayList<>();
        for (CombinedFlowStepDto stepDto : combinedFlowDto.getFlowSteps()) {
            // Create test data entries first
            List<Long> testDataIds = createTestDataEntries(stepDto.getTestData());
            
            // Create flow step
            FlowStep flowStep = new FlowStep();
            flowStep.setApplicationId(stepDto.getApplicationId());
            flowStep.setBranch(stepDto.getBranch());
            flowStep.setTestTag(stepDto.getTestTag());
            flowStep.setTestStage(stepDto.getTestStage());
            flowStep.setDescription(stepDto.getDescription());
            flowStep.setSquashStepIds(stepDto.getSquashStepIds());
            flowStep.setTestDataIds(testDataIds);
            flowStep.setInvokeScheduler(convertInvokeSchedulerDtoToEntity(stepDto.getInvokeScheduler()));
            
            FlowStep savedFlowStep = flowStepRepository.save(flowStep);
            savedFlowSteps.add(savedFlowStep);
            flowStepIds.add(savedFlowStep.getId());
        }
        
        // Create flow
        Flow flow = new Flow();
        flow.setFlowStepIds(flowStepIds);
        flow.setSquashTestCaseId(combinedFlowDto.getSquashTestCaseId());
        flow.setSquashTestCase(combinedFlowDto.getSquashTestCase());
        
        Flow savedFlow = flowRepository.save(flow);
        
        logger.info("Combined flow created with ID: {}", savedFlow.getId());
        return convertToDto(savedFlow, savedFlowSteps);
    }

    @Transactional(readOnly = true)
    public Optional<CombinedFlowDto> getCombinedFlowById(Long id) {
        logger.debug("Fetching combined flow with ID: {}", id);
        
        Optional<Flow> flowOpt = flowRepository.findById(id);
        if (flowOpt.isEmpty()) {
            return Optional.empty();
        }
        
        Flow flow = flowOpt.get();
        List<FlowStep> flowSteps = flowStepRepository.findByIdIn(flow.getFlowStepIds());
        
        return Optional.of(convertToDto(flow, flowSteps));
    }

    @Transactional(readOnly = true)
    public List<CombinedFlowDto> getAllCombinedFlows() {
        logger.debug("Fetching all combined flows");
        
        List<Flow> flows = flowRepository.findAll();
        return flows.stream()
                .map(flow -> {
                    List<FlowStep> flowSteps = flowStepRepository.findByIdIn(flow.getFlowStepIds());
                    return convertToDto(flow, flowSteps);
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<CombinedFlowDto> getAllCombinedFlows(Pageable pageable) {
        logger.debug("Fetching all combined flows with pagination: {}", pageable);
        return flowRepository.findAll(pageable)
                .map(flow -> {
                    List<FlowStep> flowSteps = flowStepRepository.findByIdIn(flow.getFlowStepIds());
                    return convertToDto(flow, flowSteps);
                });
    }

    @Transactional(readOnly = true)
    public List<CombinedFlowDto> getCombinedFlowsBySquashTestCaseId(Long squashTestCaseId) {
        logger.debug("Fetching combined flows for Squash test case ID: {}", squashTestCaseId);
        
        List<Flow> flows = flowRepository.findBySquashTestCaseId(squashTestCaseId);
        return flows.stream()
                .map(flow -> {
                    List<FlowStep> flowSteps = flowStepRepository.findByIdIn(flow.getFlowStepIds());
                    return convertToDto(flow, flowSteps);
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<CombinedFlowDto> getCombinedFlowsBySquashTestCaseId(Long squashTestCaseId, Pageable pageable) {
        logger.debug("Fetching combined flows for Squash test case ID: {} with pagination: {}", squashTestCaseId, pageable);
        return flowRepository.findBySquashTestCaseId(squashTestCaseId, pageable)
                .map(flow -> {
                    List<FlowStep> flowSteps = flowStepRepository.findByIdIn(flow.getFlowStepIds());
                    return convertToDto(flow, flowSteps);
                });
    }

    public CombinedFlowDto updateCombinedFlow(Long id, CombinedFlowDto combinedFlowDto) {
        logger.info("Updating combined flow with ID: {}", id);
        
        Flow existingFlow = flowRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Flow not found with ID: " + id));
        
        // Validate all applications exist
        validateApplicationsExist(combinedFlowDto.getFlowSteps());
        
        // Delete old flow steps and their test data
        List<FlowStep> oldFlowSteps = flowStepRepository.findByIdIn(existingFlow.getFlowStepIds());
        deleteFlowStepsAndTestData(oldFlowSteps);
        
        // Create new flow steps with test data
        List<Long> newFlowStepIds = new ArrayList<>();
        List<FlowStep> newFlowSteps = new ArrayList<>();
        
        for (CombinedFlowStepDto stepDto : combinedFlowDto.getFlowSteps()) {
            // Create test data entries first
            List<Long> testDataIds = createTestDataEntries(stepDto.getTestData());
            
            // Create flow step
            FlowStep flowStep = new FlowStep();
            flowStep.setApplicationId(stepDto.getApplicationId());
            flowStep.setBranch(stepDto.getBranch());
            flowStep.setTestTag(stepDto.getTestTag());
            flowStep.setTestStage(stepDto.getTestStage());
            flowStep.setDescription(stepDto.getDescription());
            flowStep.setSquashStepIds(stepDto.getSquashStepIds());
            flowStep.setTestDataIds(testDataIds);
            flowStep.setInvokeScheduler(convertInvokeSchedulerDtoToEntity(stepDto.getInvokeScheduler()));
            
            FlowStep savedFlowStep = flowStepRepository.save(flowStep);
            newFlowSteps.add(savedFlowStep);
            newFlowStepIds.add(savedFlowStep.getId());
        }
        
        // Update flow
        existingFlow.setFlowStepIds(newFlowStepIds);
        existingFlow.setSquashTestCaseId(combinedFlowDto.getSquashTestCaseId());
        existingFlow.setSquashTestCase(combinedFlowDto.getSquashTestCase());
        
        Flow updatedFlow = flowRepository.save(existingFlow);
        
        logger.info("Combined flow updated successfully with ID: {}", updatedFlow.getId());
        return convertToDto(updatedFlow, newFlowSteps);
    }

    public void deleteCombinedFlow(Long id) {
        logger.info("Deleting combined flow with ID: {}", id);
        
        Flow flow = flowRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Flow not found with ID: " + id));
        
        // Delete flow steps but only unlink test data (don't delete test data)
        List<FlowStep> flowSteps = flowStepRepository.findByIdIn(flow.getFlowStepIds());
        unlinkFlowStepsAndTestData(flowSteps);
        
        // Delete flow
        flowRepository.deleteById(id);
        logger.info("Combined flow deleted successfully with ID: {}", id);
    }

    private void validateApplicationsExistFromCreateDto(List<FlowStepCreateDto> flowSteps) {
        List<Long> applicationIds = flowSteps.stream()
                .map(FlowStepCreateDto::getApplicationId)
                .distinct()
                .collect(Collectors.toList());
        
        for (Long applicationId : applicationIds) {
            if (!applicationRepository.existsById(applicationId)) {
                throw new IllegalArgumentException("Application not found with ID: " + applicationId);
            }
        }
    }

    private void validateTestDataExist(List<FlowStepCreateDto> flowSteps) {
        List<Long> testDataIds = flowSteps.stream()
                .filter(step -> step.getTestData() != null && !step.getTestData().isEmpty())
                .flatMap(step -> step.getTestData().stream())
                .distinct()
                .collect(Collectors.toList());
        
        for (Long testDataId : testDataIds) {
            if (!testDataRepository.existsByDataId(testDataId)) {
                throw new IllegalArgumentException("Test data not found with ID: " + testDataId);
            }
        }
    }

    private void unlinkFlowStepsAndTestData(List<FlowStep> flowSteps) {
        for (FlowStep flowStep : flowSteps) {
            // Just delete flow step - test data remains in the database
            flowStepRepository.delete(flowStep);
        }
    }

    private void validateApplicationsExist(List<CombinedFlowStepDto> flowSteps) {
        List<Long> applicationIds = flowSteps.stream()
                .map(CombinedFlowStepDto::getApplicationId)
                .distinct()
                .collect(Collectors.toList());
        
        for (Long applicationId : applicationIds) {
            if (!applicationRepository.existsById(applicationId)) {
                throw new IllegalArgumentException("Application not found with ID: " + applicationId);
            }
        }
    }

    private List<Long> createTestDataEntries(List<TestDataDto> testDataDtoList) {
        if (testDataDtoList == null || testDataDtoList.isEmpty()) {
            return new ArrayList<>();
        }
        
        return testDataDtoList.stream()
                .map(testDataService::createTestData)
                .map(TestDataDto::getDataId)
                .collect(Collectors.toList());
    }

    private void deleteFlowStepsAndTestData(List<FlowStep> flowSteps) {
        for (FlowStep flowStep : flowSteps) {
            // Delete associated test data
            if (flowStep.getTestDataIds() != null && !flowStep.getTestDataIds().isEmpty()) {
                testDataRepository.deleteByDataIdIn(flowStep.getTestDataIds());
            }
            // Delete flow step
            flowStepRepository.delete(flowStep);
        }
    }

    private CombinedFlowDto convertToDto(Flow flow, List<FlowStep> flowSteps) {
        CombinedFlowDto dto = new CombinedFlowDto();
        dto.setId(flow.getId());
        dto.setSquashTestCaseId(flow.getSquashTestCaseId());
        dto.setSquashTestCase(flow.getSquashTestCase());
        dto.setCreatedAt(flow.getCreatedAt());
        dto.setUpdatedAt(flow.getUpdatedAt());
        
        // Convert flow steps
        List<CombinedFlowStepDto> flowStepDtos = flowSteps.stream()
                .map(this::convertFlowStepToDto)
                .collect(Collectors.toList());
        
        dto.setFlowSteps(flowStepDtos);
        return dto;
    }

    private CombinedFlowStepDto convertFlowStepToDto(FlowStep flowStep) {
        CombinedFlowStepDto dto = new CombinedFlowStepDto();
        dto.setId(flowStep.getId());
        dto.setApplicationId(flowStep.getApplicationId());
        dto.setBranch(flowStep.getBranch());
        dto.setTestTag(flowStep.getTestTag());
        dto.setTestStage(flowStep.getTestStage());
        dto.setDescription(flowStep.getDescription());
        dto.setSquashStepIds(flowStep.getSquashStepIds());
        dto.setInvokeScheduler(convertInvokeSchedulerEntityToDto(flowStep.getInvokeScheduler()));
        dto.setCreatedAt(flowStep.getCreatedAt());
        dto.setUpdatedAt(flowStep.getUpdatedAt());
        
        // Get test data
        if (flowStep.getTestDataIds() != null && !flowStep.getTestDataIds().isEmpty()) {
            List<TestDataDto> testDataDtos = flowStep.getTestDataIds().stream()
                    .map(testDataService::getTestDataById)
                    .collect(Collectors.toList());
            dto.setTestData(testDataDtos);
        } else {
            dto.setTestData(new ArrayList<>());
        }
        
        return dto;
    }

    private InvokeScheduler convertInvokeSchedulerDtoToEntity(InvokeSchedulerDto dto) {
        if (dto == null) {
            return null;
        }
        
        InvokeScheduler entity = new InvokeScheduler();
        entity.setType(dto.getType());
        
        if (dto.getTimer() != null) {
            Timer timer = new Timer();
            timer.setMinutes(dto.getTimer().getMinutes());
            timer.setHours(dto.getTimer().getHours());
            timer.setDays(dto.getTimer().getDays());
            entity.setTimer(timer);
        }
        
        return entity;
    }

    private InvokeSchedulerDto convertInvokeSchedulerEntityToDto(InvokeScheduler entity) {
        if (entity == null) {
            return null;
        }
        
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
}

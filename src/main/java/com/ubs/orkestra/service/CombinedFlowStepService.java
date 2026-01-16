package com.ubs.orkestra.service;

import com.ubs.orkestra.dto.CombinedFlowStepDto;
import com.ubs.orkestra.dto.FlowStepCreateDto;
import com.ubs.orkestra.dto.TestDataDto;
import com.ubs.orkestra.dto.InvokeSchedulerDto;
import com.ubs.orkestra.dto.TimerDto;
import com.ubs.orkestra.model.FlowStep;
import com.ubs.orkestra.model.InvokeScheduler;
import com.ubs.orkestra.model.Timer;
import com.ubs.orkestra.repository.ApplicationRepository;
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
public class CombinedFlowStepService {

    private static final Logger logger = LoggerFactory.getLogger(CombinedFlowStepService.class);

    @Autowired
    private FlowStepRepository flowStepRepository;

    @Autowired
    private TestDataRepository testDataRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private TestDataService testDataService;

    public CombinedFlowStepDto createFlowStepFromCreateDto(FlowStepCreateDto flowStepCreateDto) {
        logger.info("Creating new flow step from create DTO for application ID: {}", flowStepCreateDto.getApplicationId());
        
        // Validate that the application exists
        if (!applicationRepository.existsById(flowStepCreateDto.getApplicationId())) {
            throw new IllegalArgumentException("Application not found with ID: " + flowStepCreateDto.getApplicationId());
        }
        
        // Validate test data IDs exist
        if (flowStepCreateDto.getTestData() != null && !flowStepCreateDto.getTestData().isEmpty()) {
            for (Long testDataId : flowStepCreateDto.getTestData()) {
                if (!testDataRepository.existsByDataId(testDataId)) {
                    throw new IllegalArgumentException("Test data not found with ID: " + testDataId);
                }
            }
        }
        
        // Create flow step
        FlowStep flowStep = new FlowStep();
        applicationRepository.findById(flowStepCreateDto.getApplicationId()).ifPresent(flowStep::setApplication);
        flowStep.setBranch(flowStepCreateDto.getBranch());
        flowStep.setTestTag(flowStepCreateDto.getTestTag());
        flowStep.setTestStage(flowStepCreateDto.getTestStage());
        flowStep.setDescription(flowStepCreateDto.getDescription());
        flowStep.setSquashStepIds(flowStepCreateDto.getSquashStepIds());
        flowStep.setTestDataIds(flowStepCreateDto.getTestData() != null ? flowStepCreateDto.getTestData() : new ArrayList<>());
        
        // Handle optional invokeScheduler
        if (flowStepCreateDto.getInvokeScheduler() != null) {
            flowStep.setInvokeScheduler(convertInvokeSchedulerDtoToEntity(flowStepCreateDto.getInvokeScheduler()));
        }
        
        FlowStep savedFlowStep = flowStepRepository.save(flowStep);
        
        logger.info("Flow step created with ID: {}", savedFlowStep.getId());
        return convertToDto(savedFlowStep);
    }

    public CombinedFlowStepDto updateFlowStepFromCreateDto(Long id, FlowStepCreateDto flowStepCreateDto) {
        logger.info("Updating flow step with ID: {} from create DTO", id);
        
        FlowStep existingFlowStep = flowStepRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Flow step not found with ID: " + id));
        
        // Validate that the application exists if it's being changed
        if (!existingFlowStep.getApplication().getId().equals(flowStepCreateDto.getApplicationId()) &&
            !applicationRepository.existsById(flowStepCreateDto.getApplicationId())) {
            throw new IllegalArgumentException("Application not found with ID: " + flowStepCreateDto.getApplicationId());
        }
        
        // Validate test data IDs exist
        if (flowStepCreateDto.getTestData() != null && !flowStepCreateDto.getTestData().isEmpty()) {
            for (Long testDataId : flowStepCreateDto.getTestData()) {
                if (!testDataRepository.existsByDataId(testDataId)) {
                    throw new IllegalArgumentException("Test data not found with ID: " + testDataId);
                }
            }
        }
        
        // Update flow step (don't delete test data - just unlink)
        applicationRepository.findById(flowStepCreateDto.getApplicationId()).ifPresent(existingFlowStep::setApplication);
        existingFlowStep.setBranch(flowStepCreateDto.getBranch());
        existingFlowStep.setTestTag(flowStepCreateDto.getTestTag());
        existingFlowStep.setTestStage(flowStepCreateDto.getTestStage());
        existingFlowStep.setDescription(flowStepCreateDto.getDescription());
        existingFlowStep.setSquashStepIds(flowStepCreateDto.getSquashStepIds());
        existingFlowStep.setTestDataIds(flowStepCreateDto.getTestData() != null ? flowStepCreateDto.getTestData() : new ArrayList<>());
        
        // Handle optional invokeScheduler
        if (flowStepCreateDto.getInvokeScheduler() != null) {
            existingFlowStep.setInvokeScheduler(convertInvokeSchedulerDtoToEntity(flowStepCreateDto.getInvokeScheduler()));
        } else {
            existingFlowStep.setInvokeScheduler(null);
        }
        
        FlowStep updatedFlowStep = flowStepRepository.save(existingFlowStep);
        
        logger.info("Flow step updated successfully with ID: {}", updatedFlowStep.getId());
        return convertToDto(updatedFlowStep);
    }

    public CombinedFlowStepDto createFlowStep(CombinedFlowStepDto flowStepDto) {
        logger.info("Creating new flow step for application ID: {}", flowStepDto.getApplicationId());
        
        // Validate that the application exists
        if (!applicationRepository.existsById(flowStepDto.getApplicationId())) {
            throw new IllegalArgumentException("Application not found with ID: " + flowStepDto.getApplicationId());
        }
        
        // Create test data entries first
        List<Long> testDataIds = createTestDataEntries(flowStepDto.getTestData());
        
        // Create flow step
        FlowStep flowStep = new FlowStep();
        applicationRepository.findById(flowStepDto.getApplicationId()).ifPresent(flowStep::setApplication);
        flowStep.setBranch(flowStepDto.getBranch());
        flowStep.setTestTag(flowStepDto.getTestTag());
        flowStep.setTestStage(flowStepDto.getTestStage());
        flowStep.setDescription(flowStepDto.getDescription());
        flowStep.setSquashStepIds(flowStepDto.getSquashStepIds());
        flowStep.setTestDataIds(testDataIds);
        
        // Handle optional invokeScheduler
        if (flowStepDto.getInvokeScheduler() != null) {
            flowStep.setInvokeScheduler(convertInvokeSchedulerDtoToEntity(flowStepDto.getInvokeScheduler()));
        }
        
        FlowStep savedFlowStep = flowStepRepository.save(flowStep);
        
        logger.info("Flow step created with ID: {}", savedFlowStep.getId());
        return convertToDto(savedFlowStep);
    }

    @Transactional(readOnly = true)
    public Optional<CombinedFlowStepDto> getFlowStepById(Long id) {
        logger.debug("Fetching flow step with ID: {}", id);
        return flowStepRepository.findById(id)
                .map(this::convertToDto);
    }

    @Transactional(readOnly = true)
    public List<CombinedFlowStepDto> getAllFlowSteps() {
        logger.debug("Fetching all flow steps");
        return flowStepRepository.findAll()
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<CombinedFlowStepDto> getAllFlowSteps(Pageable pageable) {
        logger.debug("Fetching all flow steps with pagination: {}", pageable);
        return flowStepRepository.findAll(pageable)
                .map(this::convertToDto);
    }

    @Transactional(readOnly = true)
    public List<CombinedFlowStepDto> getFlowStepsByApplicationId(Long applicationId) {
        logger.debug("Fetching flow steps for application ID: {}", applicationId);
        return applicationRepository.findById(applicationId)
                .map(application -> flowStepRepository.findByApplication(application)
                        .stream()
                        .map(this::convertToDto)
                        .collect(Collectors.toList()))
                .orElseThrow(() -> new IllegalArgumentException("Application not found with ID: " + applicationId));
    }

    @Transactional(readOnly = true)
    public Page<CombinedFlowStepDto> getFlowStepsByApplicationId(Long applicationId, Pageable pageable) {
        logger.debug("Fetching flow steps for application ID: {} with pagination: {}", applicationId, pageable);
        return applicationRepository.findById(applicationId)
                .map(application -> flowStepRepository.findByApplication(application, pageable)
                        .map(this::convertToDto))
                .orElseThrow(() -> new IllegalArgumentException("Application not found with ID: " + applicationId));
    }

    public CombinedFlowStepDto updateFlowStep(Long id, CombinedFlowStepDto flowStepDto) {
        logger.info("Updating flow step with ID: {}", id);
        
        FlowStep existingFlowStep = flowStepRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Flow step not found with ID: " + id));
        
        // Validate that the application exists if it's being changed
        if (!existingFlowStep.getApplication().getId().equals(flowStepDto.getApplicationId()) &&
            !applicationRepository.existsById(flowStepDto.getApplicationId())) {
            throw new IllegalArgumentException("Application not found with ID: " + flowStepDto.getApplicationId());
        }
        
        // Delete old test data
        if (existingFlowStep.getTestDataIds() != null && !existingFlowStep.getTestDataIds().isEmpty()) {
            testDataRepository.deleteByDataIdIn(existingFlowStep.getTestDataIds());
        }
        
        // Create new test data entries
        List<Long> newTestDataIds = createTestDataEntries(flowStepDto.getTestData());
        
        // Update flow step
        applicationRepository.findById(flowStepDto.getApplicationId()).ifPresent(existingFlowStep::setApplication);
        existingFlowStep.setBranch(flowStepDto.getBranch());
        existingFlowStep.setTestTag(flowStepDto.getTestTag());
        existingFlowStep.setTestStage(flowStepDto.getTestStage());
        existingFlowStep.setDescription(flowStepDto.getDescription());
        existingFlowStep.setSquashStepIds(flowStepDto.getSquashStepIds());
        existingFlowStep.setTestDataIds(newTestDataIds);
        
        // Handle optional invokeScheduler
        if (flowStepDto.getInvokeScheduler() != null) {
            existingFlowStep.setInvokeScheduler(convertInvokeSchedulerDtoToEntity(flowStepDto.getInvokeScheduler()));
        } else {
            existingFlowStep.setInvokeScheduler(null);
        }
        
        FlowStep updatedFlowStep = flowStepRepository.save(existingFlowStep);
        
        logger.info("Flow step updated successfully with ID: {}", updatedFlowStep.getId());
        return convertToDto(updatedFlowStep);
    }

    public void deleteFlowStep(Long id) {
        logger.info("Deleting flow step with ID: {}", id);
        
        FlowStep flowStep = flowStepRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Flow step not found with ID: " + id));
        
        // Only delete flow step - test data remains in the database (just unlinked)
        flowStepRepository.deleteById(id);
        logger.info("Flow step deleted successfully with ID: {} (test data was unlinked, not deleted)", id);
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

    private CombinedFlowStepDto convertToDto(FlowStep flowStep) {
        CombinedFlowStepDto dto = new CombinedFlowStepDto();
        dto.setId(flowStep.getId());
        if (flowStep.getApplication() != null) {
            dto.setApplicationId(flowStep.getApplication().getId());
        }
        dto.setBranch(flowStep.getBranch());
        dto.setTestTag(flowStep.getTestTag());
        dto.setTestStage(flowStep.getTestStage());
        dto.setDescription(flowStep.getDescription());
        dto.setSquashStepIds(flowStep.getSquashStepIds());
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
        
        // Handle optional invokeScheduler
        if (flowStep.getInvokeScheduler() != null) {
            dto.setInvokeScheduler(convertInvokeSchedulerEntityToDto(flowStep.getInvokeScheduler()));
        }
        
        return dto;
    }
    
    private InvokeScheduler convertInvokeSchedulerDtoToEntity(InvokeSchedulerDto dto) {
        if (dto == null) return null;
        
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
}

package com.ubs.orkestra.service;

import com.ubs.orkestra.dto.FlowStepDto;
import com.ubs.orkestra.dto.InvokeSchedulerDto;
import com.ubs.orkestra.dto.TimerDto;
import com.ubs.orkestra.model.FlowStep;
import com.ubs.orkestra.model.InvokeScheduler;
import com.ubs.orkestra.model.Timer;
import com.ubs.orkestra.repository.ApplicationRepository;
import com.ubs.orkestra.repository.FlowStepRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class FlowStepService {

    private static final Logger logger = LoggerFactory.getLogger(FlowStepService.class);

    @Autowired
    private FlowStepRepository flowStepRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    public FlowStepDto createFlowStep(FlowStepDto flowStepDto) {
        logger.info("Creating new flow step for application ID: {}", flowStepDto.getApplicationId());
        
        // Validate that the application exists
        if (!applicationRepository.existsById(flowStepDto.getApplicationId())) {
            throw new IllegalArgumentException("Application not found with ID: " + flowStepDto.getApplicationId());
        }
        
        FlowStep flowStep = convertToEntity(flowStepDto);
        FlowStep savedFlowStep = flowStepRepository.save(flowStep);
        
        logger.info("Flow step created with ID: {}", savedFlowStep.getId());
        return convertToDto(savedFlowStep);
    }

    @Transactional(readOnly = true)
    public Optional<FlowStepDto> getFlowStepById(Long id) {
        logger.debug("Fetching flow step with ID: {}", id);
        return flowStepRepository.findById(id)
                .map(this::convertToDto);
    }

    @Transactional(readOnly = true)
    public List<FlowStepDto> getAllFlowSteps() {
        logger.debug("Fetching all flow steps");
        return flowStepRepository.findAll()
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<FlowStepDto> getFlowStepsByApplicationId(Long applicationId) {
        logger.debug("Fetching flow steps for application ID: {}", applicationId);
        return flowStepRepository.findByApplicationId(applicationId)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<FlowStepDto> getFlowStepsByIds(List<Long> ids) {
        logger.debug("Fetching flow steps by IDs: {}", ids);
        return flowStepRepository.findByIdIn(ids)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public FlowStepDto updateFlowStep(Long id, FlowStepDto flowStepDto) {
        logger.info("Updating flow step with ID: {}", id);
        
        FlowStep existingFlowStep = flowStepRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Flow step not found with ID: " + id));
        
        // Validate that the application exists if it's being changed
        if (!existingFlowStep.getApplicationId().equals(flowStepDto.getApplicationId()) &&
            !applicationRepository.existsById(flowStepDto.getApplicationId())) {
            throw new IllegalArgumentException("Application not found with ID: " + flowStepDto.getApplicationId());
        }
        
        existingFlowStep.setApplicationId(flowStepDto.getApplicationId());
        existingFlowStep.setBranch(flowStepDto.getBranch());
        existingFlowStep.setTestTag(flowStepDto.getTestTag());
        existingFlowStep.setTestStage(flowStepDto.getTestStage());
        existingFlowStep.setSquashStepIds(flowStepDto.getSquashStepIds());
        existingFlowStep.setTestDataIds(flowStepDto.getTestDataIds());
        
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
        
        if (!flowStepRepository.existsById(id)) {
            throw new IllegalArgumentException("Flow step not found with ID: " + id);
        }
        
        flowStepRepository.deleteById(id);
        logger.info("Flow step deleted successfully with ID: {}", id);
    }

    private FlowStep convertToEntity(FlowStepDto dto) {
        FlowStep flowStep = new FlowStep();
        flowStep.setApplicationId(dto.getApplicationId());
        flowStep.setBranch(dto.getBranch());
        flowStep.setTestTag(dto.getTestTag());
        flowStep.setTestStage(dto.getTestStage());
        flowStep.setSquashStepIds(dto.getSquashStepIds());
        flowStep.setTestDataIds(dto.getTestDataIds());
        
        // Handle optional invokeScheduler
        if (dto.getInvokeScheduler() != null) {
            flowStep.setInvokeScheduler(convertInvokeSchedulerDtoToEntity(dto.getInvokeScheduler()));
        }
        
        return flowStep;
    }

    private FlowStepDto convertToDto(FlowStep entity) {
        FlowStepDto dto = new FlowStepDto();
        dto.setId(entity.getId());
        dto.setApplicationId(entity.getApplicationId());
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
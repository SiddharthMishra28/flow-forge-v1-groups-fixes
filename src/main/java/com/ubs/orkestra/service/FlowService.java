package com.ubs.orkestra.service;

import com.ubs.orkestra.dto.FlowDto;
import com.ubs.orkestra.model.Flow;
import com.ubs.orkestra.repository.FlowRepository;
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
public class FlowService {

    private static final Logger logger = LoggerFactory.getLogger(FlowService.class);

    @Autowired
    private FlowRepository flowRepository;

    @Autowired
    private FlowStepRepository flowStepRepository;

    public FlowDto createFlow(FlowDto flowDto) {
        logger.info("Creating new flow with {} steps", flowDto.getFlowStepIds().size());
        
        // Validate that all flow steps exist
        List<Long> existingStepIds = flowStepRepository.findByIdIn(flowDto.getFlowStepIds())
                .stream()
                .map(step -> step.getId())
                .collect(Collectors.toList());
        
        List<Long> missingStepIds = flowDto.getFlowStepIds().stream()
                .filter(id -> !existingStepIds.contains(id))
                .collect(Collectors.toList());
        
        if (!missingStepIds.isEmpty()) {
            throw new IllegalArgumentException("Flow steps not found with IDs: " + missingStepIds);
        }
        
        Flow flow = convertToEntity(flowDto);
        Flow savedFlow = flowRepository.save(flow);
        
        logger.info("Flow created with ID: {}", savedFlow.getId());
        return convertToDto(savedFlow);
    }

    @Transactional(readOnly = true)
    public Optional<FlowDto> getFlowById(Long id) {
        logger.debug("Fetching flow with ID: {}", id);
        return flowRepository.findById(id)
                .map(this::convertToDto);
    }

    @Transactional(readOnly = true)
    public List<FlowDto> getAllFlows() {
        logger.debug("Fetching all flows");
        return flowRepository.findAll()
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<FlowDto> getFlowsBySquashTestCaseId(Long squashTestCaseId) {
        logger.debug("Fetching flows for Squash test case ID: {}", squashTestCaseId);
        return flowRepository.findBySquashTestCaseId(squashTestCaseId)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public FlowDto updateFlow(Long id, FlowDto flowDto) {
        logger.info("Updating flow with ID: {}", id);
        
        Flow existingFlow = flowRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Flow not found with ID: " + id));
        
        // Validate that all flow steps exist
        List<Long> existingStepIds = flowStepRepository.findByIdIn(flowDto.getFlowStepIds())
                .stream()
                .map(step -> step.getId())
                .collect(Collectors.toList());
        
        List<Long> missingStepIds = flowDto.getFlowStepIds().stream()
                .filter(stepId -> !existingStepIds.contains(stepId))
                .collect(Collectors.toList());
        
        if (!missingStepIds.isEmpty()) {
            throw new IllegalArgumentException("Flow steps not found with IDs: " + missingStepIds);
        }
        
        existingFlow.setFlowStepIds(flowDto.getFlowStepIds());
        existingFlow.setSquashTestCaseId(flowDto.getSquashTestCaseId());
        existingFlow.setSquashTestCase(flowDto.getSquashTestCase());
        
        Flow updatedFlow = flowRepository.save(existingFlow);
        
        logger.info("Flow updated successfully with ID: {}", updatedFlow.getId());
        return convertToDto(updatedFlow);
    }

    public void deleteFlow(Long id) {
        logger.info("Deleting flow with ID: {}", id);
        
        if (!flowRepository.existsById(id)) {
            throw new IllegalArgumentException("Flow not found with ID: " + id);
        }
        
        flowRepository.deleteById(id);
        logger.info("Flow deleted successfully with ID: {}", id);
    }

    public FlowDto addTestCaseToFlow(Long flowId, Long squashTestCaseId) {
        logger.info("Adding SquashTM test case {} to flow {}", squashTestCaseId, flowId);
        
        Flow existingFlow = flowRepository.findById(flowId)
                .orElseThrow(() -> new IllegalArgumentException("Flow not found with ID: " + flowId));
        
        existingFlow.setSquashTestCaseId(squashTestCaseId);
        Flow updatedFlow = flowRepository.save(existingFlow);
        
        logger.info("Successfully associated SquashTM test case {} with flow {}", squashTestCaseId, flowId);
        return convertToDto(updatedFlow);
    }

    public FlowDto removeTestCaseFromFlow(Long flowId) {
        logger.info("Removing SquashTM test case association from flow {}", flowId);
        
        Flow existingFlow = flowRepository.findById(flowId)
                .orElseThrow(() -> new IllegalArgumentException("Flow not found with ID: " + flowId));
        
        Long previousTestCaseId = existingFlow.getSquashTestCaseId();
        existingFlow.setSquashTestCaseId(null);
        Flow updatedFlow = flowRepository.save(existingFlow);
        
        logger.info("Successfully removed SquashTM test case {} association from flow {}", previousTestCaseId, flowId);
        return convertToDto(updatedFlow);
    }

    public FlowDto updateTestCaseInFlow(Long flowId, Long newSquashTestCaseId) {
        logger.info("Updating SquashTM test case in flow {} to test case {}", flowId, newSquashTestCaseId);
        
        Flow existingFlow = flowRepository.findById(flowId)
                .orElseThrow(() -> new IllegalArgumentException("Flow not found with ID: " + flowId));
        
        Long previousTestCaseId = existingFlow.getSquashTestCaseId();
        existingFlow.setSquashTestCaseId(newSquashTestCaseId);
        Flow updatedFlow = flowRepository.save(existingFlow);
        
        logger.info("Successfully updated SquashTM test case in flow {} from {} to {}", 
                   flowId, previousTestCaseId, newSquashTestCaseId);
        return convertToDto(updatedFlow);
    }

    private Flow convertToEntity(FlowDto dto) {
        Flow flow = new Flow();
        flow.setFlowStepIds(dto.getFlowStepIds());
        flow.setSquashTestCaseId(dto.getSquashTestCaseId());
        flow.setSquashTestCase(dto.getSquashTestCase());
        return flow;
    }

    private FlowDto convertToDto(Flow entity) {
        FlowDto dto = new FlowDto();
        dto.setId(entity.getId());
        dto.setFlowStepIds(entity.getFlowStepIds());
        dto.setSquashTestCaseId(entity.getSquashTestCaseId());
        dto.setSquashTestCase(entity.getSquashTestCase());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
package com.ubs.orkestra.service;

import com.ubs.orkestra.dto.PipelineExecutionDto;
import com.ubs.orkestra.model.PipelineExecution;
import com.ubs.orkestra.repository.PipelineExecutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PipelineExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(PipelineExecutionService.class);

    @Autowired
    private PipelineExecutionRepository pipelineExecutionRepository;

    public List<PipelineExecutionDto> getPipelineExecutionsByFlowExecutionId(UUID flowExecutionId) {
        logger.debug("Fetching pipeline executions for flow execution ID: {} (including replays)", flowExecutionId);
        return pipelineExecutionRepository.findByFlowExecutionIdIncludingReplays(flowExecutionId)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public Page<PipelineExecutionDto> getPipelineExecutionsByFlowExecutionId(UUID flowExecutionId, Pageable pageable) {
        logger.debug("Fetching pipeline executions for flow execution ID: {} with pagination: {}", flowExecutionId, pageable);
        return pipelineExecutionRepository.findByFlowExecutionId(flowExecutionId, pageable)
                .map(this::convertToDto);
    }

    public Optional<PipelineExecutionDto> getPipelineExecutionById(Long pipelineExecutionId) {
        logger.debug("Fetching pipeline execution with ID: {}", pipelineExecutionId);
        return pipelineExecutionRepository.findById(pipelineExecutionId)
                .map(this::convertToDto);
    }

    public List<PipelineExecutionDto> getPipelineExecutionsByFlowId(Long flowId) {
        logger.debug("Fetching pipeline executions for flow ID: {}", flowId);
        return pipelineExecutionRepository.findByFlowId(flowId)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public List<PipelineExecutionDto> getPipelineExecutionsByFlowStepId(Long flowStepId) {
        logger.debug("Fetching pipeline executions for flow step ID: {} (including replays)", flowStepId);
        return pipelineExecutionRepository.findByFlowStepIdIncludingReplays(flowStepId)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    private PipelineExecutionDto convertToDto(PipelineExecution entity) {
        PipelineExecutionDto dto = new PipelineExecutionDto();
        dto.setId(entity.getId());
        dto.setFlowId(entity.getFlowId());
        dto.setFlowExecutionId(entity.getFlowExecutionId());
        dto.setFlowStepId(entity.getFlowStepId());
        dto.setPipelineId(entity.getPipelineId());
        dto.setPipelineUrl(entity.getPipelineUrl());
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

package com.ubs.orkestra.service;

import com.ubs.orkestra.dto.FlowExecutionDto;
import com.ubs.orkestra.dto.FlowGroupCreateDto;
import com.ubs.orkestra.dto.FlowGroupDetailsDto;
import com.ubs.orkestra.dto.FlowGroupDto;
import com.ubs.orkestra.dto.FlowGroupUpdateDto;
import com.ubs.orkestra.model.Flow;
import com.ubs.orkestra.model.FlowGroup;
import com.ubs.orkestra.repository.FlowGroupRepository;
import com.ubs.orkestra.repository.FlowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class FlowGroupService {

    private static final Logger logger = LoggerFactory.getLogger(FlowGroupService.class);

    @Autowired
    private FlowGroupRepository flowGroupRepository;

    @Autowired
    private FlowRepository flowRepository;

    @Autowired
    private FlowExecutionService flowExecutionService;

    public FlowGroupDto createFlowGroup(FlowGroupCreateDto flowGroupCreateDto) {
        logger.info("Creating new flow group: {}", flowGroupCreateDto.getFlowGroupName());

        FlowGroup flowGroup = new FlowGroup(flowGroupCreateDto.getFlowGroupName(), flowGroupCreateDto.getFlows());
        FlowGroup saved = flowGroupRepository.save(flowGroup);

        return convertToDto(saved);
    }

    public FlowGroupDto createFlowGroup(FlowGroupDto flowGroupDto) {
        logger.info("Creating new flow group: {}", flowGroupDto.getFlowGroupName());

        FlowGroup flowGroup = new FlowGroup(flowGroupDto.getFlowGroupName(), flowGroupDto.getFlows());
        FlowGroup saved = flowGroupRepository.save(flowGroup);

        return convertToDto(saved);
    }

    public Optional<FlowGroupDto> getFlowGroupById(Long id) {
        logger.debug("Fetching flow group with ID: {}", id);

        return flowGroupRepository.findById(id)
                .map(this::convertToDto);
    }

    public Page<FlowGroupDto> getAllFlowGroups(Pageable pageable) {
        logger.debug("Fetching all flow groups with pagination");

        return flowGroupRepository.findAll(pageable)
                .map(this::convertToDto);
    }

    public FlowGroupDto updateFlowGroup(Long id, FlowGroupUpdateDto flowGroupUpdateDto) {
        logger.info("Updating flow group with ID: {}", id);

        FlowGroup existing = flowGroupRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("FlowGroup not found with ID: " + id));

        existing.setFlowGroupName(flowGroupUpdateDto.getFlowGroupName());
        existing.setFlows(flowGroupUpdateDto.getFlows());

        FlowGroup updated = flowGroupRepository.save(existing);
        return convertToDto(updated);
    }

    public FlowGroupDto updateFlowGroup(Long id, FlowGroupDto flowGroupDto) {
        logger.info("Updating flow group with ID: {}", id);

        FlowGroup existing = flowGroupRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("FlowGroup not found with ID: " + id));

        existing.setFlowGroupName(flowGroupDto.getFlowGroupName());
        existing.setFlows(flowGroupDto.getFlows());

        FlowGroup updated = flowGroupRepository.save(existing);
        return convertToDto(updated);
    }

    public void deleteFlowGroup(Long id) {
        logger.info("Deleting flow group with ID: {}", id);

        if (!flowGroupRepository.existsById(id)) {
            throw new IllegalArgumentException("FlowGroup not found with ID: " + id);
        }

        flowGroupRepository.deleteById(id);
    }

    public FlowGroupDto patchFlowGroup(Long id, List<Long> addFlows, List<Long> removeFlows) {
        logger.info("Patching flow group with ID: {} - adding {} flows, removing {} flows", id,
                   addFlows != null ? addFlows.size() : 0, removeFlows != null ? removeFlows.size() : 0);

        FlowGroup existing = flowGroupRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("FlowGroup not found with ID: " + id));

        List<Long> currentFlows = existing.getFlows();
        if (currentFlows == null) {
            currentFlows = new java.util.ArrayList<>();
        }

        // Remove flows
        if (removeFlows != null && !removeFlows.isEmpty()) {
            currentFlows.removeAll(removeFlows);
        }

        // Add flows (avoid duplicates)
        if (addFlows != null && !addFlows.isEmpty()) {
            for (Long flowId : addFlows) {
                if (!currentFlows.contains(flowId)) {
                    currentFlows.add(flowId);
                }
            }
        }

        existing.setFlows(currentFlows);
        FlowGroup updated = flowGroupRepository.save(existing);

        return convertToDto(updated);
    }

    public Map<String, Object> executeFlowGroup(Long flowGroupId) {
        logger.info("Executing flow group with ID: {}", flowGroupId);

        FlowGroup flowGroup = flowGroupRepository.findById(flowGroupId)
                .orElseThrow(() -> new IllegalArgumentException("FlowGroup not found with ID: " + flowGroupId));

        // Update iteration counters
        int currentIteration = flowGroup.getCurrentIteration() + 1;
        int revolutions = flowGroup.getRevolutions();
        if (currentIteration > 100) {
            currentIteration = 1;
            revolutions += 1;
        }
        flowGroup.setCurrentIteration(currentIteration);
        flowGroup.setRevolutions(revolutions);
        flowGroupRepository.save(flowGroup);

        List<Long> flowIds = flowGroup.getFlows();
        if (flowIds == null || flowIds.isEmpty()) {
            throw new IllegalArgumentException("FlowGroup has no flows to execute");
        }

        String flowIdsStr = flowIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        Map<String, Object> result = flowExecutionService.executeMultipleFlows(flowIdsStr, flowGroupId, currentIteration, revolutions, flowGroup.getFlowGroupName());

        // Start async execution for all accepted flows - this happens after we have the response ready
        @SuppressWarnings("unchecked")
        List<FlowExecutionDto> acceptedExecutions = (List<FlowExecutionDto>) result.get("accepted");
        logger.info("FlowGroup execution created {} accepted flow executions", acceptedExecutions != null ? acceptedExecutions.size() : 0);
        if (acceptedExecutions != null) {
            for (FlowExecutionDto executionDto : acceptedExecutions) {
                flowExecutionService.executeFlowAsync(executionDto.getId());
                logger.info("Started async execution for flow ID: {} with execution ID: {}",
                           executionDto.getFlowId(), executionDto.getId());
            }
        }

        return result;
    }

    public FlowGroupDetailsDto getFlowGroupDetails(String flowGroupName, Integer page, Integer size, String sortBy, String sortDirection) {
        logger.info("Getting flow group details with filters");

        List<FlowGroup> flowGroups = flowGroupRepository.findAll();

        // Filter by flowGroupName if provided
        if (flowGroupName != null && !flowGroupName.trim().isEmpty()) {
            flowGroups = flowGroups.stream()
                    .filter(fg -> fg.getFlowGroupName().toLowerCase().contains(flowGroupName.toLowerCase()))
                    .collect(Collectors.toList());
        }

        // TODO: Implement pagination and sorting if needed
        // For now, return all filtered results

        Map<String, FlowGroupDetailsDto.FlowGroupDetail> flowGroupsMap = new HashMap<>();

        for (FlowGroup flowGroup : flowGroups) {
            List<Flow> flows = flowRepository.findAllById(flowGroup.getFlows());
            List<FlowGroupDetailsDto.FlowSummaryDto> flowSummaries = flows.stream()
                    .map(flow -> new FlowGroupDetailsDto.FlowSummaryDto(
                            flow.getId(),
                            flow.getSquashTestCase(),
                            flow.getSquashTestCaseId().intValue(),
                            flow.getCreatedAt(),
                            flow.getUpdatedAt()))
                    .collect(Collectors.toList());

            FlowGroupDetailsDto.FlowGroupDetail flowGroupDetail = new FlowGroupDetailsDto.FlowGroupDetail(
                    flowGroup.getId(), flowSummaries);
            flowGroupsMap.put(flowGroup.getFlowGroupName(), flowGroupDetail);
        }

        return new FlowGroupDetailsDto(flowGroupsMap);
    }

    private FlowGroupDto convertToDto(FlowGroup flowGroup) {
        return new FlowGroupDto(
                flowGroup.getId(),
                flowGroup.getFlowGroupName(),
                flowGroup.getFlows(),
                flowGroup.getCreatedAt(),
                flowGroup.getUpdatedAt(),
                flowGroup.getCurrentIteration(),
                flowGroup.getRevolutions()
        );
    }
}

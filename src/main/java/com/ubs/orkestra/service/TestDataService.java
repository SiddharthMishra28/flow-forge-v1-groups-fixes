package com.ubs.orkestra.service;

import com.ubs.orkestra.dto.TestDataDto;
import com.ubs.orkestra.model.TestData;
import com.ubs.orkestra.repository.ApplicationRepository;
import com.ubs.orkestra.repository.FlowStepRepository;
import com.ubs.orkestra.repository.TestDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class TestDataService {

    @Autowired
    private TestDataRepository testDataRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private FlowStepRepository flowStepRepository;

    public TestDataDto createTestData(TestDataDto testDataDto) {
        if (testDataDto.getApplicationId() == null || !applicationRepository.existsById(testDataDto.getApplicationId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or missing applicationId");
        }
        TestData testData = new TestData();
        testData.setApplicationId(testDataDto.getApplicationId());
        testData.setApplicationName(testDataDto.getApplicationName());
        testData.setCategory(testDataDto.getCategory());
        testData.setDescription(testDataDto.getDescription());
        testData.setVariables(testDataDto.getVariables());
        
        TestData savedTestData = testDataRepository.save(testData);
        return convertToDto(savedTestData);
    }

    public TestDataDto getTestDataById(Long dataId) {
        TestData testData = testDataRepository.findById(dataId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TestData not found with id: " + dataId));
        return convertToDto(testData);
    }

    public List<TestDataDto> getAllTestData() {
        return testDataRepository.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public List<TestDataDto> getAllTestData(Long applicationId) {
        if (applicationId != null) {
            if (!applicationRepository.existsById(applicationId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid applicationId: " + applicationId);
            }
            return testDataRepository.findByApplicationId(applicationId).stream()
                    .map(this::convertToDto)
                    .collect(Collectors.toList());
        } else {
            return getAllTestData();
        }
    }

    public Page<TestDataDto> getAllTestData(Pageable pageable) {
        return testDataRepository.findAll(pageable)
                .map(this::convertToDto);
    }

    public Page<TestDataDto> getAllTestData(Pageable pageable, Long applicationId) {
        if (applicationId != null) {
            if (!applicationRepository.existsById(applicationId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid applicationId: " + applicationId);
            }
            return testDataRepository.findByApplicationId(applicationId, pageable)
                    .map(this::convertToDto);
        } else {
            return getAllTestData(pageable);
        }
    }

    public TestDataDto updateTestData(Long dataId, TestDataDto testDataDto) {
        TestData testData = testDataRepository.findById(dataId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TestData not found with id: " + dataId));
        
        if (testDataDto.getApplicationId() != null) {
            if (!applicationRepository.existsById(testDataDto.getApplicationId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid applicationId");
            }
            testData.setApplicationId(testDataDto.getApplicationId());
        }

        if (testDataDto.getApplicationName() != null) {
            testData.setApplicationName(testDataDto.getApplicationName());
        }

        if (testDataDto.getCategory() != null) {
            testData.setCategory(testDataDto.getCategory());
        }

        if (testDataDto.getDescription() != null) {
            testData.setDescription(testDataDto.getDescription());
        }
        
        testData.setVariables(testDataDto.getVariables());
        TestData updatedTestData = testDataRepository.save(testData);
        return convertToDto(updatedTestData);
    }

    public void deleteTestData(Long dataId) {
        if (!testDataRepository.existsById(dataId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "TestData not found with id: " + dataId);
        }

        // Check if TestData is being used by any FlowStep
        if (flowStepRepository.existsByTestDataId(dataId.toString())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Cannot delete TestData with id: " + dataId + ". It is currently referenced by one or more FlowSteps.");
        }

        testDataRepository.deleteById(dataId);
    }

    public List<TestDataDto> getTestDataByApplicationId(Long applicationId) {
        if (applicationId == null || !applicationRepository.existsById(applicationId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or missing applicationId");
        }
        return testDataRepository.findByApplicationId(applicationId).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public List<TestDataDto> searchTestData(String applicationName, String category, String description) {
        return testDataRepository.searchByFilters(applicationName, category, description)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public Page<TestDataDto> searchTestData(String applicationName, String category, String description, Pageable pageable) {
        return testDataRepository.searchByFilters(applicationName, category, description, pageable)
                .map(this::convertToDto);
    }

    /**
     * Merges multiple test data maps into a single map.
     * Used by FlowExecutionService to merge all test data for a flow step.
     */
    public Map<String, String> mergeTestDataByIds(List<Long> testDataIds) {
        if (testDataIds == null || testDataIds.isEmpty()) {
            return new HashMap<>();
        }

        List<TestData> testDataList = testDataRepository.findByDataIdIn(testDataIds);
        Map<String, String> mergedData = new HashMap<>();
        
        for (TestData testData : testDataList) {
            if (testData.getVariables() != null) {
                mergedData.putAll(testData.getVariables());
            }
        }
        
        return mergedData;
    }

    private TestDataDto convertToDto(TestData testData) {
        TestDataDto dto = new TestDataDto();
        dto.setDataId(testData.getDataId());
        dto.setApplicationId(testData.getApplicationId());
        dto.setApplicationName(testData.getApplicationName());
        dto.setCategory(testData.getCategory());
        dto.setDescription(testData.getDescription());
        dto.setVariables(testData.getVariables());
        dto.setCreatedAt(testData.getCreatedAt());
        dto.setUpdatedAt(testData.getUpdatedAt());
        return dto;
    }
}
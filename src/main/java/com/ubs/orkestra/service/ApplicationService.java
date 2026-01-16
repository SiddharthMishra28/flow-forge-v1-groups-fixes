package com.ubs.orkestra.service;

import com.ubs.orkestra.dto.ApplicationDto;
import com.ubs.orkestra.dto.BranchDto;
import com.ubs.orkestra.dto.ValidationResponseDto;
import com.ubs.orkestra.exception.GitLabValidationException;
import com.ubs.orkestra.model.Application;
import com.ubs.orkestra.repository.ApplicationRepository;
import com.ubs.orkestra.config.GitLabConfig;
import com.ubs.orkestra.util.GitLabApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class ApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationService.class);

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private GitLabApiClient gitLabApiClient;

    @Autowired
    private GitLabConfig gitLabConfig;

    @Autowired
    private EncryptionService encryptionService;

    public ApplicationDto createApplication(ApplicationDto applicationDto) {
        logger.info("Creating new application for GitLab project: {}", applicationDto.getGitlabProjectId());
        
        if (applicationRepository.existsByGitlabProjectId(applicationDto.getGitlabProjectId())) {
            throw new IllegalArgumentException("Application with GitLab project ID " + 
                                             applicationDto.getGitlabProjectId() + " already exists");
        }
        
        Application application = convertToEntity(applicationDto);

        // Validate GitLab connection before creating application (skip in mock mode)
        if (!gitLabConfig.isMockMode()) {
            ValidationResponseDto validationResponse = validateGitLabConnectionInternal(
                applicationDto.getPersonalAccessToken(),
                applicationDto.getGitlabProjectId()
            );

            if (!validationResponse.isValid()) {
                throw new GitLabValidationException("GitLab connection validation failed: " + validationResponse.getMessage());
            }

            // Set project name and URL from validation response
            application.setProjectName(validationResponse.getProjectName());
            application.setProjectUrl(validationResponse.getProjectUrl());
        } else {
            // In mock mode, set default project name and URL
            application.setProjectName("Mock Project");
            application.setProjectUrl("https://mock-gitlab.com/mock-project");
            logger.info("Skipping GitLab validation in mock mode for project: {}", applicationDto.getGitlabProjectId());
        }
        
        Application savedApplication = applicationRepository.save(application);
        
        logger.info("Application created with ID: {}", savedApplication.getId());
        return convertToDto(savedApplication);
    }

    @Transactional(readOnly = true)
    public Optional<ApplicationDto> getApplicationById(Long id) {
        logger.debug("Fetching application with ID: {}", id);
        return applicationRepository.findById(id)
                .map(this::convertToDto);
    }

    @Transactional(readOnly = true)
    public List<ApplicationDto> getAllApplications() {
        logger.debug("Fetching all applications");
        return applicationRepository.findAll()
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<ApplicationDto> getAllApplications(Pageable pageable) {
        logger.debug("Fetching all applications with pagination: {}", pageable);
        return applicationRepository.findAll(pageable)
                .map(this::convertToDto);
    }

    public ApplicationDto updateApplication(Long id, ApplicationDto applicationDto) {
        logger.info("Updating application with ID: {}", id);
        
        Application existingApplication = applicationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Application not found with ID: " + id));
        
        // Check if GitLab project ID is being changed and if it conflicts with existing ones
        if (!existingApplication.getGitlabProjectId().equals(applicationDto.getGitlabProjectId()) &&
            applicationRepository.existsByGitlabProjectId(applicationDto.getGitlabProjectId())) {
            throw new IllegalArgumentException("Application with GitLab project ID " + 
                                             applicationDto.getGitlabProjectId() + " already exists");
        }
        
        // Validate GitLab connection if project ID or token has changed
        boolean projectIdChanged = !existingApplication.getGitlabProjectId().equals(applicationDto.getGitlabProjectId());
        // Decrypt existing token to compare with new token
        String existingDecryptedToken = encryptionService.decrypt(existingApplication.getPersonalAccessToken());
        boolean tokenChanged = !existingDecryptedToken.equals(applicationDto.getPersonalAccessToken());
        
        if (projectIdChanged || tokenChanged) {
            ValidationResponseDto validationResponse = validateGitLabConnectionInternal(
                applicationDto.getPersonalAccessToken(), 
                applicationDto.getGitlabProjectId()
            );
            
            if (!validationResponse.isValid()) {
                throw new GitLabValidationException("GitLab connection validation failed: " + validationResponse.getMessage());
            }
            
            // Update project name and URL from validation response
            existingApplication.setProjectName(validationResponse.getProjectName());
            existingApplication.setProjectUrl(validationResponse.getProjectUrl());
        }
        
        existingApplication.setGitlabProjectId(applicationDto.getGitlabProjectId());
        // Encrypt the new personal access token before saving
        existingApplication.setPersonalAccessToken(encryptionService.encrypt(applicationDto.getPersonalAccessToken()));
        existingApplication.setApplicationName(applicationDto.getApplicationName());
        existingApplication.setApplicationDescription(applicationDto.getApplicationDescription());
        
        Application updatedApplication = applicationRepository.save(existingApplication);
        
        logger.info("Application updated successfully with ID: {}", updatedApplication.getId());
        return convertToDto(updatedApplication);
    }

    public void deleteApplication(Long id) {
        logger.info("Deleting application with ID: {}", id);
        
        if (!applicationRepository.existsById(id)) {
            throw new IllegalArgumentException("Application not found with ID: " + id);
        }
        
        applicationRepository.deleteById(id);
        logger.info("Application deleted successfully with ID: {}", id);
    }

    /**
     * Get decrypted personal access token for an application.
     * This method is intended for use by other services that need the actual token.
     */
    public String getDecryptedPersonalAccessToken(Long applicationId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found with ID: " + applicationId));
        return encryptionService.decrypt(application.getPersonalAccessToken());
    }

    /**
     * Get decrypted personal access token by GitLab project ID.
     * This method is intended for use by other services that need the actual token.
     */
    public String getDecryptedPersonalAccessTokenByProjectId(String gitlabProjectId) {
        Application application = applicationRepository.findByGitlabProjectId(gitlabProjectId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found with GitLab project ID: " + gitlabProjectId));
        return encryptionService.decrypt(application.getPersonalAccessToken());
    }

    public ValidationResponseDto validateGitLabConnection(String accessToken, String projectId) {
        return validateGitLabConnectionInternal(accessToken, projectId);
    }

    /**
     * Get all branches for a specific application's GitLab repository
     */
    @Transactional(readOnly = true)
    public List<BranchDto> getApplicationBranches(Long applicationId) {
        logger.info("Fetching branches for application ID: {}", applicationId);
        
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found with ID: " + applicationId));
        
        logger.debug("Found application: {}, Project ID: {}, Token Status: {}", 
                    application.getApplicationName(), application.getGitlabProjectId(), application.getTokenStatus());
        
        // Check if the token is active
        if (application.getTokenStatus() != null && 
            !application.getTokenStatus().name().equals("ACTIVE")) {
            logger.warn("Application {} has inactive token status: {}", 
                       applicationId, application.getTokenStatus());
            throw new RuntimeException("Application token is not active. Current status: " + application.getTokenStatus());
        }
        
        try {
            // Decrypt the personal access token
            String decryptedToken = encryptionService.decrypt(application.getPersonalAccessToken());
            logger.debug("Successfully decrypted access token for application ID: {}", applicationId);
            
            // Log the GitLab configuration being used
            logger.debug("Using GitLab base URL: {}, Project ID: {}", 
                        gitLabConfig.getBaseUrl(), application.getGitlabProjectId());
            
            // First validate that the token works by checking project access
            logger.debug("Validating GitLab connection before fetching branches...");
            GitLabApiClient.GitLabProjectResponse projectValidation = gitLabApiClient
                .validateConnection(gitLabConfig.getBaseUrl(), application.getGitlabProjectId(), decryptedToken)
                .block();
            
            if (projectValidation == null) {
                logger.error("GitLab project validation returned null for application ID: {}", applicationId);
                throw new RuntimeException("Unable to validate GitLab project access");
            }
            
            logger.debug("GitLab project validation successful for: {}", projectValidation.getName());
            
            // Now fetch the branches
            GitLabApiClient.GitLabBranchResponse[] branchResponses = gitLabApiClient
                .getProjectBranches(gitLabConfig.getBaseUrl(), application.getGitlabProjectId(), decryptedToken)
                .block(); // Blocking call for synchronous response
            
            if (branchResponses != null) {
                logger.info("Successfully fetched {} branches for application ID: {}", branchResponses.length, applicationId);
                
                return Arrays.stream(branchResponses)
                        .map(this::convertBranchToDto)
                        .collect(Collectors.toList());
            } else {
                logger.warn("No branches found for application ID: {}", applicationId);
                return List.of();
            }
        } catch (Exception e) {
            logger.error("Failed to fetch branches for application ID {}: {}", applicationId, e.getMessage(), e);
            
            String errorMessage = "Failed to fetch branches";
            if (e.getMessage() != null) {
                if (e.getMessage().contains("401") || e.getMessage().contains("Unauthorized")) {
                    errorMessage = "Invalid access token or insufficient permissions";
                } else if (e.getMessage().contains("403") || e.getMessage().contains("Forbidden")) {
                    errorMessage = "Access forbidden - check token permissions";
                } else if (e.getMessage().contains("404") || e.getMessage().contains("Not Found")) {
                    errorMessage = "Project not found or access denied";
                } else if (e.getMessage().contains("timeout") || e.getMessage().contains("TimeoutException")) {
                    errorMessage = "Connection timeout - GitLab server may be unreachable";
                } else {
                    errorMessage = "Failed to fetch branches: " + e.getMessage();
                }
            }
            
            throw new RuntimeException(errorMessage, e);
        }
    }

    private ValidationResponseDto validateGitLabConnectionInternal(String accessToken, String projectId) {
        logger.info("Validating GitLab connection for project: {}", projectId);
        
        try {
            GitLabApiClient.GitLabProjectResponse projectResponse = gitLabApiClient
                .validateConnection(gitLabConfig.getBaseUrl(), projectId, accessToken)
                .block(); // Blocking call for synchronous response
            
            if (projectResponse != null) {
                logger.info("GitLab connection validation successful for project: {} ({})", 
                           projectResponse.getName(), projectResponse.getNameWithNamespace());
                
                return new ValidationResponseDto(
                    true,
                    "GitLab connection successful",
                    projectResponse.getNameWithNamespace(),
                    projectResponse.getWebUrl()
                );
            } else {
                logger.warn("GitLab connection validation failed - no response received for project: {}", projectId);
                return new ValidationResponseDto(false, "GitLab connection failed - no response received");
            }
        } catch (Exception e) {
            logger.error("GitLab connection validation failed for project {}: {}", projectId, e.getMessage());
            
            String errorMessage = "GitLab connection failed";
            if (e.getMessage() != null) {
                if (e.getMessage().contains("401")) {
                    errorMessage = "Invalid access token or insufficient permissions";
                } else if (e.getMessage().contains("403")) {
                    errorMessage = "Access forbidden - check token permissions";
                } else if (e.getMessage().contains("404")) {
                    errorMessage = "Project not found or access denied";
                } else if (e.getMessage().contains("timeout") || e.getMessage().contains("TimeoutException")) {
                    errorMessage = "Connection timeout - GitLab server may be unreachable";
                } else {
                    errorMessage = "GitLab connection failed: " + e.getMessage();
                }
            }
            
            // For validation endpoint, return ValidationResponseDto with error
            return new ValidationResponseDto(false, errorMessage);
        }
    }

    private Application convertToEntity(ApplicationDto dto) {
        Application application = new Application();
        application.setGitlabProjectId(dto.getGitlabProjectId());
        // Encrypt the personal access token before saving
        application.setPersonalAccessToken(encryptionService.encrypt(dto.getPersonalAccessToken()));
        application.setApplicationName(dto.getApplicationName());
        application.setApplicationDescription(dto.getApplicationDescription());
        application.setProjectName(dto.getProjectName());
        application.setProjectUrl(dto.getProjectUrl());
        // Set default token status as ACTIVE for new applications
        if (dto.getTokenStatus() != null) {
            application.setTokenStatus(dto.getTokenStatus());
        }
        return application;
    }

    private ApplicationDto convertToDto(Application entity) {
        ApplicationDto dto = new ApplicationDto();
        dto.setId(entity.getId());
        dto.setGitlabProjectId(entity.getGitlabProjectId());
        // Don't set the personal access token in response (it's write-only)
        // dto.setPersonalAccessToken() is intentionally omitted
        dto.setApplicationName(entity.getApplicationName());
        dto.setApplicationDescription(entity.getApplicationDescription());
        dto.setProjectName(entity.getProjectName());
        dto.setProjectUrl(entity.getProjectUrl());
        dto.setTokenStatus(entity.getTokenStatus());
        dto.setTokenValidationLastUpdateDate(entity.getTokenValidationLastUpdateDate());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private BranchDto convertBranchToDto(GitLabApiClient.GitLabBranchResponse branchResponse) {
        BranchDto branchDto = new BranchDto();
        branchDto.setName(branchResponse.getName());
        branchDto.setDefault(branchResponse.isDefault());
        branchDto.setProtected(branchResponse.isProtected());
        branchDto.setMerged(branchResponse.isMerged());
        branchDto.setDevelopersCanPush(branchResponse.isDevelopersCanPush());
        branchDto.setDevelopersCanMerge(branchResponse.isDevelopersCanMerge());
        branchDto.setCanPush(branchResponse.isCanPush());
        branchDto.setWebUrl(branchResponse.getWebUrl());
        
        // Convert commit information if available
        if (branchResponse.getCommit() != null) {
            BranchDto.Commit commitDto = new BranchDto.Commit();
            GitLabApiClient.GitLabCommit gitLabCommit = branchResponse.getCommit();
            
            commitDto.setId(gitLabCommit.getId());
            commitDto.setMessage(gitLabCommit.getMessage());
            commitDto.setShortId(gitLabCommit.getShortId());
            commitDto.setAuthorName(gitLabCommit.getAuthorName());
            commitDto.setAuthorEmail(gitLabCommit.getAuthorEmail());
            commitDto.setCommittedDate(gitLabCommit.getCommittedDate());
            commitDto.setCreatedAt(gitLabCommit.getCreatedAt());
            commitDto.setWebUrl(gitLabCommit.getWebUrl());
            
            branchDto.setCommit(commitDto);
        }
        
        return branchDto;
    }
}

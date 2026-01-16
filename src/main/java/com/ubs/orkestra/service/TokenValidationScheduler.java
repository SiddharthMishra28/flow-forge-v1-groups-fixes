package com.ubs.orkestra.service;

import com.ubs.orkestra.dto.ValidationResponseDto;
import com.ubs.orkestra.enums.TokenStatus;
import com.ubs.orkestra.model.Application;
import com.ubs.orkestra.repository.ApplicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TokenValidationScheduler {

    private static final Logger logger = LoggerFactory.getLogger(TokenValidationScheduler.class);

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private EncryptionService encryptionService;

    /**
     * Scheduled job that runs based on configured cron expression to validate all application tokens.
     * Updates token status to VALID or EXPIRED based on validation results.
     */
    @Scheduled(cron = "${scheduling.token-validation.cron:0 0 2 * * *}")
    @Transactional
    public void validateAllTokensScheduled() {
        logger.info("Starting scheduled token validation job...");

        try {
            // Update the token validation timestamp for all applications
            LocalDateTime validationTimestamp = LocalDateTime.now();
            int updatedCount = applicationRepository.updateTokenValidationLastUpdateDateForAll(validationTimestamp);
            logger.info("Updated token validation timestamp for {} applications", updatedCount);

            List<Application> applications = applicationRepository.findAll();
            logger.info("Found {} applications to validate", applications.size());
            
            int validTokens = 0;
            int expiredTokens = 0;
            int errorCount = 0;
            
            for (Application application : applications) {
                try {
                    // Decrypt the token for validation
                    String decryptedToken = encryptionService.decrypt(application.getPersonalAccessToken());
                    
                    // Use the existing validation endpoint logic
                    ValidationResponseDto validationResponse = applicationService.validateGitLabConnection(
                        decryptedToken, 
                        application.getGitlabProjectId()
                    );
                    
                    TokenStatus newStatus;
                    if (validationResponse.isValid()) {
                        newStatus = TokenStatus.ACTIVE; // Using ACTIVE as VALID equivalent
                        validTokens++;
                        logger.debug("Token validation successful for application ID: {} (Project: {})", 
                                   application.getId(), application.getGitlabProjectId());
                    } else {
                        newStatus = TokenStatus.EXPIRED;
                        expiredTokens++;
                        logger.warn("Token validation failed for application ID: {} (Project: {}). Message: {}", 
                                  application.getId(), application.getGitlabProjectId(), validationResponse.getMessage());
                    }
                    
                    // Update token status if it has changed
                    if (application.getTokenStatus() != newStatus) {
                        application.setTokenStatus(newStatus);
                        applicationRepository.save(application);
                        logger.info("Updated token status for application ID: {} from {} to {}", 
                                  application.getId(), application.getTokenStatus(), newStatus);
                    }
                    
                } catch (Exception e) {
                    errorCount++;
                    logger.error("Error validating token for application ID: {} (Project: {}): {}", 
                               application.getId(), application.getGitlabProjectId(), e.getMessage(), e);
                    
                    // Mark as expired if we can't validate it
                    if (application.getTokenStatus() != TokenStatus.EXPIRED) {
                        application.setTokenStatus(TokenStatus.EXPIRED);
                        applicationRepository.save(application);
                        logger.warn("Marked application ID: {} as EXPIRED due to validation error", application.getId());
                    }
                }
                
                // Add a small delay between validations to avoid overwhelming GitLab API
                try {
                    Thread.sleep(1000); // 1 second delay
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.warn("Token validation job interrupted");
                    break;
                }
            }
            
            logger.info("Token validation job completed. Valid: {}, Expired: {}, Errors: {}, Total: {}", 
                       validTokens, expiredTokens, errorCount, applications.size());
                       
        } catch (Exception e) {
            logger.error("Failed to execute token validation job: {}", e.getMessage(), e);
        }
    }
}

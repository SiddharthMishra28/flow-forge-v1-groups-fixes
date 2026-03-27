package com.ubs.orkestra.controller;

import com.ubs.orkestra.dto.SyncStatusDto;
import com.ubs.orkestra.service.SyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for data synchronization operations.
 * Provides endpoints to manually sync flow execution data with GitLab.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Sync", description = "Data synchronization endpoints")
public class SyncController {

    private static final Logger logger = LoggerFactory.getLogger(SyncController.class);

    @Autowired
    private SyncService syncService;

    /**
     * Synchronize all flow execution data with GitLab.
     * 
     * This endpoint will:
     * 1. Query all flow executions and their pipeline executions from the database
     * 2. For each pipeline with a GitLab pipeline ID, query GitLab for current status
     * 3. Update database with latest status, artifacts, and runtime data
     * 4. Register still-running pipelines for active polling
     * 
     * Use cases:
     * - After service restart to ensure all data is in sync
     * - After manual database changes
     * - When status appears out of sync with GitLab
     * - For recovery after GitLab API issues
     * 
     * @param syncOnlyRunning If true, only sync pipelines in RUNNING state. If false, sync all pipelines (default: true)
     * @return SyncStatusDto with detailed results of the sync operation
     */
    @PostMapping("/sync-data")
    @Operation(
        summary = "Synchronize flow execution data with GitLab",
        description = "Queries GitLab for current pipeline status and updates database. " +
                     "Useful for recovery after restarts or when data appears out of sync."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Sync operation completed successfully"),
        @ApiResponse(responseCode = "409", description = "Sync operation already in progress"),
        @ApiResponse(responseCode = "500", description = "Sync operation failed")
    })
    public ResponseEntity<SyncStatusDto> syncAllData(
        @Parameter(description = "Only sync RUNNING pipelines (default: true)")
        @RequestParam(defaultValue = "true") boolean syncOnlyRunning
    ) {
        logger.info("Received request to sync all flow execution data (syncOnlyRunning: {})", syncOnlyRunning);
        
        try {
            SyncStatusDto result = syncService.syncAllFlowExecutionData(syncOnlyRunning);
            
            if (result.isInProgress() && result.getSyncedFlowExecutions() == null) {
                // Sync already in progress
                return ResponseEntity.status(409).body(result);
            }
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("Error during sync operation: {}", e.getMessage(), e);
            
            SyncStatusDto errorStatus = new SyncStatusDto();
            errorStatus.setInProgress(false);
            errorStatus.addError("Critical error: " + e.getMessage());
            errorStatus.setMessage("Sync failed: " + e.getMessage());
            
            return ResponseEntity.status(500).body(errorStatus);
        }
    }

    /**
     * Get current sync status.
     * 
     * Use this to check if a sync operation is currently in progress
     * and to get progress information.
     * 
     * @return SyncStatusDto with current sync status
     */
    @GetMapping("/sync-data/status")
    @Operation(
        summary = "Get current sync operation status",
        description = "Returns the status of the currently running sync operation, if any."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Status retrieved successfully")
    })
    public ResponseEntity<SyncStatusDto> getSyncStatus() {
        logger.debug("Received request to get sync status");
        SyncStatusDto status = syncService.getCurrentSyncStatus();
        return ResponseEntity.ok(status);
    }
}

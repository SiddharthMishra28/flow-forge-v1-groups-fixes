package com.ubs.orkestra.controller;

import com.ubs.orkestra.config.GitLabWebHookConfig;
import com.ubs.orkestra.dto.GitLabWebHookPayload;
import com.ubs.orkestra.service.PipelineWebHookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Controller for handling GitLab Pipeline WebHook events.
 * This endpoint receives notifications from GitLab when pipelines complete,
 * eliminating the need for polling-based status checks.
 */
@RestController
@RequestMapping("/api/webhooks")
@Tag(name = "WebHooks", description = "GitLab Pipeline WebHook Endpoint API")
public class PipelineWebHookController {

    private static final Logger logger = LoggerFactory.getLogger(PipelineWebHookController.class);

    @Autowired
    private PipelineWebHookService pipelineWebHookService;

    @Autowired
    private GitLabWebHookConfig gitLabWebHookConfig;

    @PostMapping("/gitlab/pipeline")
    @Operation(
        summary = "GitLab Pipeline WebHook Endpoint", 
        description = "Receives pipeline completion events from GitLab. " +
                      "Configure this URL in your GitLab project settings under Settings > Webhooks. " +
                      "The webhook should be triggered on 'Pipeline events'."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Webhook processed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid webhook payload"),
        @ApiResponse(responseCode = "401", description = "Invalid or missing secret token"),
        @ApiResponse(responseCode = "500", description = "Error processing webhook")
    })
    public ResponseEntity<?> handleGitLabPipelineWebHook(
            @RequestBody GitLabWebHookPayload payload,
            @Parameter(description = "GitLab secret token for webhook validation")
            @RequestHeader(value = "X-Gitlab-Token", required = false) String gitlabToken,
            @Parameter(description = "GitLab event type")
            @RequestHeader(value = "X-Gitlab-Event", required = false) String gitlabEvent) {
        
        logger.info("Received GitLab webhook event: {}", gitlabEvent);
        logger.debug("Webhook payload: object_kind={}, pipeline_id={}", 
                    payload.getObjectKind(), 
                    payload.getObjectAttributes() != null ? payload.getObjectAttributes().getId() : "null");

        // Validate secret token if configured
        if (gitLabWebHookConfig.getSecretToken() != null && !gitLabWebHookConfig.getSecretToken().trim().isEmpty()) {
            if (!pipelineWebHookService.validateSecretToken(gitlabToken, gitLabWebHookConfig.getSecretToken())) {
                logger.warn("Invalid webhook secret token received");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "message", "Invalid secret token"));
            }
        }

        // Validate event type
        if (gitlabEvent != null && !"Pipeline Hook".equals(gitlabEvent)) {
            logger.warn("Unexpected GitLab event type: {}", gitlabEvent);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Unsupported event type: " + gitlabEvent));
        }

        // Process the webhook
        PipelineWebHookService.WebHookProcessingResult result = 
                pipelineWebHookService.processWebHookEvent(payload, gitlabToken);

        if (result.isSuccess()) {
            logger.info("Webhook processed successfully: {}", result.getMessage());
            return ResponseEntity.ok(Map.of("success", true, "message", result.getMessage()));
        } else {
            logger.error("Webhook processing failed: {}", result.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", result.getMessage()));
        }
    }

    @GetMapping("/gitlab/webhook-url")
    @Operation(
        summary = "Get WebHook URL for Flow Execution",
        description = "Returns the webhook URL that should be configured in GitLab for a specific flow execution. " +
                      "This URL includes the flowExecutionId for routing the webhook to the correct execution."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Webhook URL generated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid flow execution ID")
    })
    public ResponseEntity<?> getWebHookUrlForFlowExecution(
            @Parameter(description = "Flow Execution UUID", required = true)
            @RequestParam UUID flowExecutionId) {
        
        if (flowExecutionId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "flowExecutionId is required"));
        }

        // The webhook URL is the same for all pipelines in a flow execution
        // The FlowExecutionId is passed as a GitLab variable and returned in the webhook
        String webhookUrl = getBaseWebhookUrl() + "/api/webhooks/gitlab/pipeline";
        
        logger.info("Generated webhook URL for flow execution {}: {}", flowExecutionId, webhookUrl);
        
        return ResponseEntity.ok(Map.of(
            "webhookUrl", webhookUrl,
            "flowExecutionId", flowExecutionId.toString(),
            "instructions", Map.of(
                "url", webhookUrl,
                "triggerEvents", new String[] {"Pipeline events"},
                "secretToken", gitLabWebHookConfig.getSecretToken() != null && !gitLabWebHookConfig.getSecretToken().trim().isEmpty() ? "configured" : "not configured",
                "note", "Configure this URL in GitLab project settings > Webhooks. The FLOW_EXECUTION_ID variable is automatically injected into each pipeline."
            )
        ));
    }

    private String getBaseWebhookUrl() {
        // This could be made configurable via application.yml
        // For now, it's a relative URL - in production, configure the external URL
        return "";
    }
}

package com.ubs.orkestra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for GitLab WebHook integration.
 */
@Configuration
@ConfigurationProperties(prefix = "gitlab.webhook")
public class GitLabWebHookConfig {

    /**
     * Secret token for webhook validation.
     * This should match the secret token configured in GitLab webhook settings.
     */
    private String secretToken;

    /**
     * External base URL for webhook callbacks.
     * This is the public URL that GitLab will use to call webhook endpoints.
     * Example: https://your-domain.com or http://localhost:8080
     * If not set, relative paths will be used (works for localhost setups).
     */
    private String externalUrl;

    /**
     * Enable automatic webhook registration when applications are created.
     * When true, webhooks are automatically registered in GitLab projects.
     * Default: false (manual webhook configuration)
     */
    private boolean autoRegister = false;

    /**
     * Webhook URL path for pipeline events.
     * Default: /api/webhooks/gitlab/pipeline
     */
    private String pipelineWebhookPath = "/api/webhooks/gitlab/pipeline";

    public String getSecretToken() {
        return secretToken;
    }

    public void setSecretToken(String secretToken) {
        this.secretToken = secretToken;
    }

    public String getExternalUrl() {
        return externalUrl;
    }

    public void setExternalUrl(String externalUrl) {
        this.externalUrl = externalUrl;
    }

    public boolean isAutoRegister() {
        return autoRegister;
    }

    public void setAutoRegister(boolean autoRegister) {
        this.autoRegister = autoRegister;
    }

    public String getPipelineWebhookPath() {
        return pipelineWebhookPath;
    }

    public void setPipelineWebhookPath(String pipelineWebhookPath) {
        this.pipelineWebhookPath = pipelineWebhookPath;
    }

    /**
     * Get the full webhook URL for pipeline events.
     * Combines externalUrl with the pipeline webhook path.
     */
    public String getPipelineWebhookUrl() {
        if (externalUrl == null || externalUrl.trim().isEmpty()) {
            return pipelineWebhookPath;
        }
        return externalUrl.replaceAll("/+$", "") + pipelineWebhookPath;
    }
}

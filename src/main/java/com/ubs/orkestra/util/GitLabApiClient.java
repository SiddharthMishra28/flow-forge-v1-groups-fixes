package com.ubs.orkestra.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class GitLabApiClient {

    private static final Logger logger = LoggerFactory.getLogger(GitLabApiClient.class);
    private final WebClient webClient;

    public GitLabApiClient() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    /**
     * Trigger a GitLab pipeline execution
     */
    public Mono<GitLabPipelineResponse> triggerPipeline(String gitlabBaseUrl, String projectId, 
                                                       String branch, String accessToken, 
                                                       Map<String, String> variables) {
        String url = String.format("%s/api/v4/projects/%s/pipeline", gitlabBaseUrl, projectId);
        
        logger.info("Triggering GitLab pipeline for project {} on branch {}", projectId, branch);
        
        GitLabPipelineRequest request = new GitLabPipelineRequest(branch, variables);
        
        return webClient.post()
                .uri(url)
                .header("PRIVATE-TOKEN", accessToken)
                .header("Content-Type", "application/json")
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                         response -> response.bodyToMono(String.class)
                                 .doOnNext(body -> logger.error("GitLab API error response: {}", body))
                                 .then(Mono.error(new RuntimeException("GitLab API error: " + response.statusCode()))))
                .bodyToMono(GitLabPipelineResponse.class)
                .timeout(Duration.ofSeconds(30))
                .doOnSuccess(response -> logger.info("Pipeline triggered successfully: {}", response.getId()))
                .doOnError(error -> logger.error("Failed to trigger pipeline: {}", error.getMessage()));
    }

    /**
     * Get pipeline status
     */
    public Mono<GitLabPipelineResponse> getPipelineStatus(String gitlabBaseUrl, String projectId, 
                                                         Long pipelineId, String accessToken) {
        String url = String.format("%s/api/v4/projects/%s/pipelines/%d", gitlabBaseUrl, projectId, pipelineId);
        
        return webClient.get()
                .uri(url)
                .header("PRIVATE-TOKEN", accessToken)
                .retrieve()
                .bodyToMono(GitLabPipelineResponse.class)
                .timeout(Duration.ofSeconds(15))
                .doOnError(error -> logger.error("Failed to get pipeline status: {}", error.getMessage()));
    }

    /**
     * Get jobs for a pipeline
     */
    public Mono<GitLabJobsResponse[]> getPipelineJobs(String gitlabBaseUrl, String projectId, 
                                                     Long pipelineId, String accessToken) {
        String url = String.format("%s/api/v4/projects/%s/pipelines/%d/jobs", 
                                  gitlabBaseUrl, projectId, pipelineId);
        
        logger.debug("Getting jobs for pipeline {}", pipelineId);
        
        return webClient.get()
                .uri(url)
                .header("PRIVATE-TOKEN", accessToken)
                .retrieve()
                .bodyToMono(GitLabJobsResponse[].class)
                .timeout(Duration.ofSeconds(30))
                .doOnError(error -> logger.error("Failed to get pipeline jobs: {}", error.getMessage()));
    }

    /**
     * Download specific artifact from a job
     */
    public Mono<String> downloadJobArtifact(String gitlabBaseUrl, String projectId, 
                                           Long jobId, String accessToken, String artifactPath) {
        String url = String.format("%s/api/v4/projects/%s/jobs/%d/artifacts/%s", 
                                  gitlabBaseUrl, projectId, jobId, artifactPath);
        
        logger.info("Downloading artifact {} from job {}", artifactPath, jobId);
        
        return webClient.get()
                .uri(url)
                .header("PRIVATE-TOKEN", accessToken)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(60))
                .doOnSuccess(content -> logger.info("Artifact downloaded successfully from job {}", jobId))
                .doOnError(error -> logger.debug("Failed to download artifact from job {}: {}", jobId, error.getMessage()));
    }

    /**
     * Validate GitLab connection by fetching project details
     */
    public Mono<GitLabProjectResponse> validateConnection(String gitlabBaseUrl, String projectId, String accessToken) {
        String url = String.format("%s/api/v4/projects/%s", gitlabBaseUrl, projectId);
        
        logger.info("Validating GitLab connection for project {} from URL: {}", projectId, url);
        logger.debug("Using access token length: {}", accessToken != null ? accessToken.length() : 0);
        
        return webClient.get()
                .uri(url)
                .header("PRIVATE-TOKEN", accessToken)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                         response -> {
                             int statusCode = response.statusCode().value();
                             logger.error("GitLab validation API returned error status: {}", statusCode);
                             
                             return response.bodyToMono(String.class)
                                     .doOnNext(body -> logger.error("GitLab validation API error response body: {}", body))
                                     .then(Mono.error(new RuntimeException(
                                         String.format("GitLab validation failed: %d %s", statusCode, 
                                                      getStatusMessage(statusCode)))));
                         })
                .bodyToMono(GitLabProjectResponse.class)
                .timeout(Duration.ofSeconds(15))
                .doOnSuccess(response -> logger.info("GitLab connection validated successfully for project: {}", response.getName()))
                .doOnError(error -> logger.error("Failed to validate GitLab connection: {}", error.getMessage()));
    }

    /**
     * Get all branches for a GitLab project
     */
    public Mono<GitLabBranchResponse[]> getProjectBranches(String gitlabBaseUrl, String projectId, String accessToken) {
        String url = String.format("%s/api/v4/projects/%s/repository/branches", gitlabBaseUrl, projectId);
        
        logger.info("Fetching branches for GitLab project {} from URL: {}", projectId, url);
        logger.debug("Using access token length: {}", accessToken != null ? accessToken.length() : 0);
        
        return webClient.get()
                .uri(url)
                .header("PRIVATE-TOKEN", accessToken)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                         response -> {
                             int statusCode = response.statusCode().value();
                             logger.error("GitLab branches API returned error status: {}", statusCode);
                             
                             return response.bodyToMono(String.class)
                                     .doOnNext(body -> logger.error("GitLab branches API error response body: {}", body))
                                     .then(Mono.error(new RuntimeException(
                                         String.format("GitLab branches API error: %d %s", statusCode, 
                                                      getStatusMessage(statusCode)))));
                         })
                .bodyToMono(GitLabBranchResponse[].class)
                .timeout(Duration.ofSeconds(30))
                .doOnSuccess(branches -> logger.info("Successfully fetched {} branches for project {}", 
                                                   branches != null ? branches.length : 0, projectId))
                .doOnError(error -> logger.error("Failed to fetch branches for project {}: {}", projectId, error.getMessage()));
    }
    
    private String getStatusMessage(int statusCode) {
        switch (statusCode) {
            case 401:
                return "Unauthorized - Invalid or expired access token";
            case 403:
                return "Forbidden - Insufficient permissions";
            case 404:
                return "Not Found - Project does not exist or no access";
            case 429:
                return "Too Many Requests - Rate limit exceeded";
            case 500:
                return "Internal Server Error - GitLab server error";
            case 502:
                return "Bad Gateway - GitLab server unavailable";
            case 503:
                return "Service Unavailable - GitLab server temporarily unavailable";
            default:
                return "Unknown error";
        }
    }

    // Inner classes for GitLab API request/response
    public static class GitLabPipelineRequest {
        private String ref;
        private List<GitLabVariable> variables;

        public GitLabPipelineRequest() {}

        public GitLabPipelineRequest(String ref, Map<String, String> variablesMap) {
            this.ref = ref;
            this.variables = new ArrayList<>();
            if (variablesMap != null) {
                variablesMap.forEach((key, value) -> 
                    this.variables.add(new GitLabVariable(key, value)));
            }
        }

        public String getRef() {
            return ref;
        }

        public void setRef(String ref) {
            this.ref = ref;
        }

        public List<GitLabVariable> getVariables() {
            return variables;
        }

        public void setVariables(List<GitLabVariable> variables) {
            this.variables = variables;
        }
    }

    public static class GitLabVariable {
        private String key;
        private String value;

        public GitLabVariable() {}

        public GitLabVariable(String key, String value) {
            this.key = key;
            this.value = value;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    public static class GitLabPipelineResponse {
        private Long id;
        private String status;
        private String ref;
        
        @com.fasterxml.jackson.annotation.JsonProperty("web_url")
        private String webUrl;
        
        @com.fasterxml.jackson.annotation.JsonProperty("created_at")
        private String createdAt;
        
        @com.fasterxml.jackson.annotation.JsonProperty("updated_at")
        private String updatedAt;

        public GitLabPipelineResponse() {}

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getRef() {
            return ref;
        }

        public void setRef(String ref) {
            this.ref = ref;
        }

        public String getWebUrl() {
            return webUrl;
        }

        public void setWebUrl(String webUrl) {
            this.webUrl = webUrl;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }

        public String getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(String updatedAt) {
            this.updatedAt = updatedAt;
        }

        public boolean isCompleted() {
            return "success".equals(status) || "failed".equals(status) || "canceled".equals(status);
        }

        public boolean isSuccessful() {
            return "success".equals(status);
        }
    }

    public static class GitLabJobsResponse {
        private Long id;
        private String name;
        private String stage;
        private String status;
        
        @com.fasterxml.jackson.annotation.JsonProperty("web_url")
        private String webUrl;
        
        @com.fasterxml.jackson.annotation.JsonProperty("created_at")
        private String createdAt;
        
        @com.fasterxml.jackson.annotation.JsonProperty("started_at")
        private String startedAt;
        
        @com.fasterxml.jackson.annotation.JsonProperty("finished_at")
        private String finishedAt;

        public GitLabJobsResponse() {}

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getStage() {
            return stage;
        }

        public void setStage(String stage) {
            this.stage = stage;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getWebUrl() {
            return webUrl;
        }

        public void setWebUrl(String webUrl) {
            this.webUrl = webUrl;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }

        public String getStartedAt() {
            return startedAt;
        }

        public void setStartedAt(String startedAt) {
            this.startedAt = startedAt;
        }

        public String getFinishedAt() {
            return finishedAt;
        }

        public void setFinishedAt(String finishedAt) {
            this.finishedAt = finishedAt;
        }

        public boolean isCompleted() {
            return "success".equals(status) || "failed".equals(status) || "canceled".equals(status);
        }

        public boolean isSuccessful() {
            return "success".equals(status);
        }
    }

    public static class GitLabProjectResponse {
        private Long id;
        private String name;
        
        @com.fasterxml.jackson.annotation.JsonProperty("name_with_namespace")
        private String nameWithNamespace;
        
        @com.fasterxml.jackson.annotation.JsonProperty("web_url")
        private String webUrl;
        
        @com.fasterxml.jackson.annotation.JsonProperty("default_branch")
        private String defaultBranch;
        
        private String description;

        public GitLabProjectResponse() {}

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getNameWithNamespace() {
            return nameWithNamespace;
        }

        public void setNameWithNamespace(String nameWithNamespace) {
            this.nameWithNamespace = nameWithNamespace;
        }

        public String getWebUrl() {
            return webUrl;
        }

        public void setWebUrl(String webUrl) {
            this.webUrl = webUrl;
        }

        public String getDefaultBranch() {
            return defaultBranch;
        }

        public void setDefaultBranch(String defaultBranch) {
            this.defaultBranch = defaultBranch;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    public static class GitLabBranchResponse {
        private String name;
        
        @com.fasterxml.jackson.annotation.JsonProperty("default")
        private boolean isDefault;
        
        @com.fasterxml.jackson.annotation.JsonProperty("protected")
        private boolean isProtected;
        
        @com.fasterxml.jackson.annotation.JsonProperty("merged")
        private boolean isMerged;
        
        @com.fasterxml.jackson.annotation.JsonProperty("developers_can_push")
        private boolean developersCanPush;
        
        @com.fasterxml.jackson.annotation.JsonProperty("developers_can_merge")
        private boolean developersCanMerge;
        
        @com.fasterxml.jackson.annotation.JsonProperty("can_push")
        private boolean canPush;
        
        @com.fasterxml.jackson.annotation.JsonProperty("web_url")
        private String webUrl;
        
        private GitLabCommit commit;

        public GitLabBranchResponse() {}

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isDefault() {
            return isDefault;
        }

        public void setDefault(boolean isDefault) {
            this.isDefault = isDefault;
        }

        public boolean isProtected() {
            return isProtected;
        }

        public void setProtected(boolean isProtected) {
            this.isProtected = isProtected;
        }

        public boolean isMerged() {
            return isMerged;
        }

        public void setMerged(boolean isMerged) {
            this.isMerged = isMerged;
        }

        public boolean isDevelopersCanPush() {
            return developersCanPush;
        }

        public void setDevelopersCanPush(boolean developersCanPush) {
            this.developersCanPush = developersCanPush;
        }

        public boolean isDevelopersCanMerge() {
            return developersCanMerge;
        }

        public void setDevelopersCanMerge(boolean developersCanMerge) {
            this.developersCanMerge = developersCanMerge;
        }

        public boolean isCanPush() {
            return canPush;
        }

        public void setCanPush(boolean canPush) {
            this.canPush = canPush;
        }

        public String getWebUrl() {
            return webUrl;
        }

        public void setWebUrl(String webUrl) {
            this.webUrl = webUrl;
        }

        public GitLabCommit getCommit() {
            return commit;
        }

        public void setCommit(GitLabCommit commit) {
            this.commit = commit;
        }
    }

    public static class GitLabCommit {
        private String id;
        private String message;
        
        @com.fasterxml.jackson.annotation.JsonProperty("short_id")
        private String shortId;
        
        @com.fasterxml.jackson.annotation.JsonProperty("author_name")
        private String authorName;
        
        @com.fasterxml.jackson.annotation.JsonProperty("author_email")
        private String authorEmail;
        
        @com.fasterxml.jackson.annotation.JsonProperty("committed_date")
        private String committedDate;
        
        @com.fasterxml.jackson.annotation.JsonProperty("created_at")
        private String createdAt;
        
        @com.fasterxml.jackson.annotation.JsonProperty("web_url")
        private String webUrl;

        public GitLabCommit() {}

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getShortId() {
            return shortId;
        }

        public void setShortId(String shortId) {
            this.shortId = shortId;
        }

        public String getAuthorName() {
            return authorName;
        }

        public void setAuthorName(String authorName) {
            this.authorName = authorName;
        }

        public String getAuthorEmail() {
            return authorEmail;
        }

        public void setAuthorEmail(String authorEmail) {
            this.authorEmail = authorEmail;
        }

        public String getCommittedDate() {
            return committedDate;
        }

        public void setCommittedDate(String committedDate) {
            this.committedDate = committedDate;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }

        public String getWebUrl() {
            return webUrl;
        }

        public void setWebUrl(String webUrl) {
            this.webUrl = webUrl;
        }
    }
}
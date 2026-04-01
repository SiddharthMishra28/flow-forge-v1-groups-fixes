package com.ubs.orkestra.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * DTO representing GitLab Pipeline WebHook payload.
 * This matches the structure sent by GitLab when a pipeline completes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitLabWebHookPayload {

    @JsonProperty("object_kind")
    private String objectKind;

    @JsonProperty("object_attributes")
    private PipelineObjectAttributes objectAttributes;

    @JsonProperty("project")
    private ProjectInfo project;

    @JsonProperty("commit")
    private CommitInfo commit;

    // Getters and Setters

    public String getObjectKind() {
        return objectKind;
    }

    public void setObjectKind(String objectKind) {
        this.objectKind = objectKind;
    }

    public PipelineObjectAttributes getObjectAttributes() {
        return objectAttributes;
    }

    public void setObjectAttributes(PipelineObjectAttributes objectAttributes) {
        this.objectAttributes = objectAttributes;
    }

    public ProjectInfo getProject() {
        return project;
    }

    public void setProject(ProjectInfo project) {
        this.project = project;
    }

    public CommitInfo getCommit() {
        return commit;
    }

    public void setCommit(CommitInfo commit) {
        this.commit = commit;
    }

    /**
     * Pipeline attributes from GitLab webhook payload
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PipelineObjectAttributes {
        @JsonProperty("id")
        private Long id;

        @JsonProperty("ref")
        private String ref;

        @JsonProperty("tag")
        private Boolean tag;

        @JsonProperty("sha")
        private String sha;

        @JsonProperty("status")
        private String status;

        @JsonProperty("source")
        private String source;

        @JsonProperty("url")
        private String url;

        @JsonProperty("detailed_status")
        private String detailedStatus;

        @JsonProperty("variables")
        private List<Variable> variables;

        // Getters and Setters

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getRef() {
            return ref;
        }

        public void setRef(String ref) {
            this.ref = ref;
        }

        public Boolean getTag() {
            return tag;
        }

        public void setTag(Boolean tag) {
            this.tag = tag;
        }

        public String getSha() {
            return sha;
        }

        public void setSha(String sha) {
            this.sha = sha;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getDetailedStatus() {
            return detailedStatus;
        }

        public void setDetailedStatus(String detailedStatus) {
            this.detailedStatus = detailedStatus;
        }

        public List<Variable> getVariables() {
            return variables;
        }

        public void setVariables(List<Variable> variables) {
            this.variables = variables;
        }

        /**
         * Variable injected into GitLab pipeline
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Variable {
            @JsonProperty("key")
            private String key;

            @JsonProperty("value")
            private String value;

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
    }

    /**
     * Project information from GitLab webhook payload
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProjectInfo {
        @JsonProperty("id")
        private Long id;

        @JsonProperty("name")
        private String name;

        @JsonProperty("path_with_namespace")
        private String pathWithNamespace;

        @JsonProperty("web_url")
        private String webUrl;

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

        public String getPathWithNamespace() {
            return pathWithNamespace;
        }

        public void setPathWithNamespace(String pathWithNamespace) {
            this.pathWithNamespace = pathWithNamespace;
        }

        public String getWebUrl() {
            return webUrl;
        }

        public void setWebUrl(String webUrl) {
            this.webUrl = webUrl;
        }
    }

    /**
     * Commit information from GitLab webhook payload
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CommitInfo {
        @JsonProperty("id")
        private String id;

        @JsonProperty("message")
        private String message;

        @JsonProperty("timestamp")
        private String timestamp;

        @JsonProperty("url")
        private String url;

        @JsonProperty("author")
        private AuthorInfo author;

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

        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public AuthorInfo getAuthor() {
            return author;
        }

        public void setAuthor(AuthorInfo author) {
            this.author = author;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class AuthorInfo {
            @JsonProperty("name")
            private String name;

            @JsonProperty("email")
            private String email;

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public String getEmail() {
                return email;
            }

            public void setEmail(String email) {
                this.email = email;
            }
        }
    }
}

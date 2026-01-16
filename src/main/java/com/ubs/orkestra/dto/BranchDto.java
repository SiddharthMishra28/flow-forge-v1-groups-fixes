package com.ubs.orkestra.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for GitLab branch information
 */
public class BranchDto {
    
    private String name;
    
    @JsonProperty("default")
    private boolean isDefault;
    
    @JsonProperty("protected")
    private boolean isProtected;
    
    @JsonProperty("merged")
    private boolean isMerged;
    
    @JsonProperty("developers_can_push")
    private boolean developersCanPush;
    
    @JsonProperty("developers_can_merge")
    private boolean developersCanMerge;
    
    @JsonProperty("can_push")
    private boolean canPush;
    
    @JsonProperty("web_url")
    private String webUrl;
    
    private Commit commit;
    
    public BranchDto() {}
    
    // Getters and Setters
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
    
    public Commit getCommit() {
        return commit;
    }
    
    public void setCommit(Commit commit) {
        this.commit = commit;
    }
    
    /**
     * Inner class for commit information
     */
    public static class Commit {
        private String id;
        private String message;
        
        @JsonProperty("short_id")
        private String shortId;
        
        @JsonProperty("author_name")
        private String authorName;
        
        @JsonProperty("author_email")
        private String authorEmail;
        
        @JsonProperty("committed_date")
        private String committedDate;
        
        @JsonProperty("created_at")
        private String createdAt;
        
        @JsonProperty("web_url")
        private String webUrl;
        
        public Commit() {}
        
        // Getters and Setters
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
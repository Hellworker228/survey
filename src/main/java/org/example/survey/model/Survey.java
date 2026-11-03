package org.example.survey.model;

import java.time.LocalDateTime;

public class Survey {

    private int id;
    private String title;
    private String description;
    private int createdBy;
    private String creatorName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String accessUsers = "*";
    private boolean active = true;
    private boolean anonymous;
    private String publicToken;
    private LocalDateTime endsAt;
    private int questionsCount;
    private int responsesCount;
    private boolean completed;
    private String accessList = "";

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(int createdBy) {
        this.createdBy = createdBy;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public void setCreatorName(String creatorName) {
        this.creatorName = creatorName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getAccessUsers() {
        return accessUsers;
    }

    public void setAccessUsers(String accessUsers) {
        this.accessUsers = accessUsers;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getEndsAt() {
        return endsAt;
    }

    public void setEndsAt(LocalDateTime endsAt) {
        this.endsAt = endsAt;
    }

    public int getQuestionsCount() {
        return questionsCount;
    }

    public void setQuestionsCount(int questionsCount) {
        this.questionsCount = questionsCount;
    }

    public int getResponsesCount() {
        return responsesCount;
    }

    public void setResponsesCount(int responsesCount) {
        this.responsesCount = responsesCount;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public String getAccessList() {
        return accessList;
    }

    public void setAccessList(String accessList) {
        this.accessList = accessList;
    }

    public boolean isAnonymous() {
        return anonymous;
    }

    public void setAnonymous(boolean anonymous) {
        this.anonymous = anonymous;
    }

    public String getPublicToken() {
        return publicToken;
    }

    public void setPublicToken(String publicToken) {
        this.publicToken = publicToken;
    }

    public boolean isPublic() {
        return accessUsers == null || "*".equals(accessUsers);
    }

    public boolean isExpired() {
        return endsAt != null && endsAt.isBefore(LocalDateTime.now());
    }

    public boolean isOpen() {
        return active && !isExpired();
    }

}

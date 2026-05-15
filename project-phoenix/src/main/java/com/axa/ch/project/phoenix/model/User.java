package com.axa.ch.project.phoenix.model;

public class User {
    private String id;
    private String sessionId;
    private String gender;
    private String jobProfile;
    private String division;
    private String generatedName;
    private String avatarUrl;
    private String groupId;
    private boolean registered;

    public User() {}

    public User(String sessionId) {
        this.sessionId = sessionId;
        this.registered = false;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getJobProfile() { return jobProfile; }
    public void setJobProfile(String jobProfile) { this.jobProfile = jobProfile; }
    public String getDivision() { return division; }
    public void setDivision(String division) { this.division = division; }
    public String getGeneratedName() { return generatedName; }
    public void setGeneratedName(String generatedName) { this.generatedName = generatedName; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public boolean isRegistered() { return registered; }
    public void setRegistered(boolean registered) { this.registered = registered; }
}
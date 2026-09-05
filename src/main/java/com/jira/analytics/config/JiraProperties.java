package com.jira.analytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jira")
public class JiraProperties {

    private String baseUrl;
    private String username;
    private String apiToken;
    private String projectKey;
    private String jql;
    private String storyPointsField = "customfield_10016";
    private String sprintField = "customfield_10020";
    private String ssoField;
    private int searchPageSize = 100;
    private int changelogPageSize = 1000;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public void setProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }

    public String getJql() {
        return jql;
    }

    public void setJql(String jql) {
        this.jql = jql;
    }

    public String getStoryPointsField() {
        return storyPointsField;
    }

    public void setStoryPointsField(String storyPointsField) {
        this.storyPointsField = storyPointsField;
    }

    public String getSprintField() {
        return sprintField;
    }

    public void setSprintField(String sprintField) {
        this.sprintField = sprintField;
    }

    public String getSsoField() {
        return ssoField;
    }

    public void setSsoField(String ssoField) {
        this.ssoField = ssoField;
    }

    public int getSearchPageSize() {
        return searchPageSize;
    }

    public void setSearchPageSize(int searchPageSize) {
        this.searchPageSize = searchPageSize;
    }

    public int getChangelogPageSize() {
        return changelogPageSize;
    }

    public void setChangelogPageSize(int changelogPageSize) {
        this.changelogPageSize = changelogPageSize;
    }
}

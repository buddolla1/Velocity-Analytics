package com.jira.analytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bitbucket")
public class BitbucketProperties {

    private String baseUrl;
    private String userLookupPath = "/rest/api/1.0/users?filter=";
    private String pullRequestDiscoveryPath = "/rest/internal/contributions/pull-requests";
    private int pageSize = 100;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getUserLookupPath() {
        return userLookupPath;
    }

    public void setUserLookupPath(String userLookupPath) {
        this.userLookupPath = userLookupPath;
    }

    public String getPullRequestDiscoveryPath() {
        return pullRequestDiscoveryPath;
    }

    public void setPullRequestDiscoveryPath(String pullRequestDiscoveryPath) {
        this.pullRequestDiscoveryPath = pullRequestDiscoveryPath;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}

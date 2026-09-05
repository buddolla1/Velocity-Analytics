package com.jira.analytics.controller;

import com.jira.analytics.dto.DashboardResponse;
import com.jira.analytics.dto.JiraSyncRequest;
import com.jira.analytics.service.JiraAnalyticsService;
import com.jira.analytics.service.JiraRestAnalyticsService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/jira")
public class JiraAnalyticsController {

    private final JiraAnalyticsService jiraAnalyticsService;
    private final JiraRestAnalyticsService jiraRestAnalyticsService;

    public JiraAnalyticsController(JiraAnalyticsService jiraAnalyticsService, JiraRestAnalyticsService jiraRestAnalyticsService) {
        this.jiraAnalyticsService = jiraAnalyticsService;
        this.jiraRestAnalyticsService = jiraRestAnalyticsService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DashboardResponse upload(@RequestParam("file") MultipartFile file) {
        return jiraAnalyticsService.process(file);
    }

    @PostMapping("/sync")
    public DashboardResponse sync(@org.springframework.web.bind.annotation.RequestBody(required = false) JiraSyncRequest request) {
        return jiraRestAnalyticsService.sync(request);
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard() {
        return jiraRestAnalyticsService.getDashboard();
    }
}

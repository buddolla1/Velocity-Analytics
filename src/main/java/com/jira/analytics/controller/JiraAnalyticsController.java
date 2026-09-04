package com.jira.analytics.controller;

import com.jira.analytics.dto.DashboardResponse;
import com.jira.analytics.service.JiraAnalyticsService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/jira")
public class JiraAnalyticsController {

    private final JiraAnalyticsService jiraAnalyticsService;

    public JiraAnalyticsController(JiraAnalyticsService jiraAnalyticsService) {
        this.jiraAnalyticsService = jiraAnalyticsService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DashboardResponse upload(@RequestParam("file") MultipartFile file) {
        return jiraAnalyticsService.process(file);
    }
}

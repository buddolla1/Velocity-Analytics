package com.jira.analytics.controller;

import com.jira.analytics.dto.PrAnalyticsResponse;
import com.jira.analytics.service.BitbucketJiraReportService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/jira-bitbucket")
public class JiraBitbucketAnalyticsController {

    private final BitbucketJiraReportService reportService;

    public JiraBitbucketAnalyticsController(BitbucketJiraReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping(value = "/analytics", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PrAnalyticsResponse analytics(
            @RequestParam("bitbucketFile") MultipartFile bitbucketFile,
            @RequestParam("jiraFile") MultipartFile jiraFile
    ) {
        return reportService.generateAnalytics(bitbucketFile, jiraFile);
    }
}

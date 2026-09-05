package com.jira.analytics.controller;

import com.jira.analytics.service.BitbucketJiraReportService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/jira-bitbucket")
public class BitbucketJiraReportController {

    private final BitbucketJiraReportService reportService;

    public BitbucketJiraReportController(BitbucketJiraReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping(
            value = "/report",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )
    public ResponseEntity<ByteArrayResource> generateReport(
            @RequestParam("bitbucketFile") MultipartFile bitbucketFile,
            @RequestParam("jiraFile") MultipartFile jiraFile
    ) {
        byte[] report = reportService.generateReport(bitbucketFile, jiraFile);
        ByteArrayResource resource = new ByteArrayResource(report);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename("bitbucket-jira-report.xlsx").build());
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        return ResponseEntity.ok()
                .headers(headers)
                .contentLength(report.length)
                .body(resource);
    }
}

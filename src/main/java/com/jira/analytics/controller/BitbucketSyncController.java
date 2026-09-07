package com.jira.analytics.controller;

import com.jira.analytics.dto.BitbucketSyncRequest;
import com.jira.analytics.dto.BitbucketSyncResult;
import com.jira.analytics.dto.ProjectOption;
import com.jira.analytics.dto.ProjectSso;
import com.jira.analytics.service.BitbucketSyncService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class BitbucketSyncController {

    private final BitbucketSyncService bitbucketSyncService;

    public BitbucketSyncController(BitbucketSyncService bitbucketSyncService) {
        this.bitbucketSyncService = bitbucketSyncService;
    }

    @GetMapping("/projects")
    public List<ProjectOption> projects() {
        return bitbucketSyncService.findProjects();
    }

    @GetMapping("/projects/{projectId}/ssos")
    public List<ProjectSso> projectSsos(@PathVariable Long projectId) {
        return bitbucketSyncService.findProjectSsos(projectId).stream()
                .map(ProjectSso::new)
                .toList();
    }

    @PostMapping("/bitbucket/sync")
    public BitbucketSyncResult sync(@RequestBody(required = false) BitbucketSyncRequest request) {
        return bitbucketSyncService.sync(request);
    }
}

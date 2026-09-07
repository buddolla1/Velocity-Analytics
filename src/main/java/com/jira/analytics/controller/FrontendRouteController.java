package com.jira.analytics.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FrontendRouteController {

    @GetMapping({
            "/",
            "/sync",
            "/overview",
            "/sprints",
            "/team",
            "/issues",
            "/pr",
            "/bitbucket/sync"
    })
    public String index() {
        return "forward:/index.html";
    }
}

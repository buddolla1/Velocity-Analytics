package com.jira.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.jira.analytics.config.JiraProperties;

@SpringBootApplication
@EnableConfigurationProperties(JiraProperties.class)
public class JiraAnalyticsApplication {

    public static void main(String[] args) {
        SpringApplication.run(JiraAnalyticsApplication.class, args);
    }
}

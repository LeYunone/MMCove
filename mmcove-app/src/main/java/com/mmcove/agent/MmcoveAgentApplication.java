package com.mmcove.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * MMCove AI Agent 应用程序入口（独立部署，无服务发现）。
 */
@SpringBootApplication
@EnableConfigurationProperties
public class MmcoveAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(MmcoveAgentApplication.class, args);
    }
}

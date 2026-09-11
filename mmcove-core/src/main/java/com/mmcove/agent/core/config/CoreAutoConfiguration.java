package com.mmcove.agent.core.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 核心模块自动配置。
 * 扫描所有核心组件以供 Spring 自动注入。
 */
@Configuration
@ComponentScan(basePackages = "com.mmcove.agent.core")
public class CoreAutoConfiguration {
}

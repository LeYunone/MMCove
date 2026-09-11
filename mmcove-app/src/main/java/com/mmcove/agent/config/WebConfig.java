package com.mmcove.agent.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Web 配置：跨域、鉴权拦截器、静态资源映射。
 */
@Configuration
@EnableAsync
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Value("${mmcove.ai.dashboard.flux.file-save-path:/tmp/flux-images}")
    private String fluxFileSavePath;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 本地鉴权拦截器：拦截所有请求，拦截器内部按路径判定是否需要认证
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        // 静态资源（SPA 托管，Phase 5）
                        "/", "/admin", "/admin/**",
                        "/assets/**", "/admin/assets/**",
                        "/favicon.ico",
                        // 健康检查 / 文生图静态资源
                        "/actuator/**", "/flux-images/**"
                );
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 文生图静态资源映射
        registry.addResourceHandler("/flux-images/**")
                .addResourceLocations("file:" + fluxFileSavePath + "/");
        // 聊天端 SPA 资源（Vite base '/'：index 在 static/chat/，资源在 static/chat/assets/）
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/chat/assets/");
        // 后台端 SPA 资源（Vite base '/admin/'：资源在 static/admin/assets/）
        registry.addResourceHandler("/admin/assets/**")
                .addResourceLocations("classpath:/static/admin/assets/");
    }

    /**
     * 虚拟线程执行器 Bean。
     * Spring 容器关闭时自动调用 shutdown()，避免线程池泄漏。
     */
    @Bean
    public ExecutorService virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}

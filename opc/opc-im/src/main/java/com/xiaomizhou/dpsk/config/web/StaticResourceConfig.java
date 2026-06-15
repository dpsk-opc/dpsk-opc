package com.xiaomizhou.dpsk.config.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/21 10:04
 * @description
 */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Value("${spring.web.resources.static-locations}")
    private String uploadPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadPath);
    }
}

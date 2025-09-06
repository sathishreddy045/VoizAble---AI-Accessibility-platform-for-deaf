package com.sathish.voizable.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // This configuration applies to all endpoints under /api/**
        registry.addMapping("/api/**")
                // Allow requests from your React application's origin
                .allowedOrigins("http://localhost:3000")
                // Specify the allowed HTTP methods
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                // Allow all headers in the request
                .allowedHeaders("*")
                // Allow credentials (like cookies), if needed in the future
                .allowCredentials(true);
    }
}

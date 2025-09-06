package com.sathish.voizable.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        // This creates a singleton RestTemplate bean that can be injected
        // anywhere in the application to make HTTP requests.
        return new RestTemplate();
    }
}

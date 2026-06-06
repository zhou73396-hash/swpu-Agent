package com.swpuagent.config;

import com.swpuagent.security.JwtAuthFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtFilterRegistration(JwtAuthFilter filter) {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        // Only filter protected API paths — unknown paths reach Spring's 404
        registration.addUrlPatterns(
                "/api/chat/*",
                "/api/db/*",
                "/api/viz/*",
                "/api/user/*"
        );
        registration.setOrder(1);
        return registration;
    }
}

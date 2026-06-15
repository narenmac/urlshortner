package com.urlshortener.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouteConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("url-service-shorten", r -> r
                        .path("/api/shorten")
                        .uri("http://url-service:8081"))
                .route("url-service-redirect", r -> r
                        .path("/api/r/**")
                        .uri("http://url-service:8081"))
                .route("url-service-health", r -> r
                        .path("/api/health")
                        .uri("http://url-service:8081"))
                .build();
    }
}

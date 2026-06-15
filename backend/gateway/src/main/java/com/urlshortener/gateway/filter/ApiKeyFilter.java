package com.urlshortener.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class ApiKeyFilter implements GlobalFilter, Ordered {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String ALLOWED_ORIGIN = "https://ourdomain";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // Allow health check without auth
        if (request.getPath().toString().equals("/api/health")) {
            return chain.filter(exchange);
        }

        // UI users: check Origin header
        String origin = request.getHeaders().getFirst(HttpHeaders.ORIGIN);
        if (origin != null && origin.startsWith(ALLOWED_ORIGIN)) {
            return chain.filter(exchange);
        }

        // API clients: check X-API-Key header
        String apiKey = request.getHeaders().getFirst(API_KEY_HEADER);
        if (apiKey == null || apiKey.isBlank()) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // TODO: Validate API key against Azure Table / Redis cache
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -1;
    }
}

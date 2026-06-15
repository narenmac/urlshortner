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

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // Allow health check without auth
        if (request.getPath().toString().equals("/api/health")) {
            return chain.filter(exchange);
        }

        // Allow requests from our own frontend (Origin or Referer matches our host)
        String origin = request.getHeaders().getFirst(HttpHeaders.ORIGIN);
        String host = request.getHeaders().getFirst(HttpHeaders.HOST);
        if (origin != null && host != null && origin.contains(host)) {
            return chain.filter(exchange);
        }

        // Allow requests with no Origin (same-origin requests from browser don't send Origin on GET)
        if (origin == null) {
            return chain.filter(exchange);
        }

        // API clients: check X-API-Key header
        String apiKey = request.getHeaders().getFirst(API_KEY_HEADER);
        if (apiKey == null || apiKey.isBlank()) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // TODO: Validate API key against Azure Table
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -1;
    }
}

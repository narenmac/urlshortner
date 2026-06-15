package com.urlshortener.service.model;

public record ShortenResponse(
        String code,
        String shortUrl,
        String originalUrl,
        String createdAt,
        boolean isNew
) {}

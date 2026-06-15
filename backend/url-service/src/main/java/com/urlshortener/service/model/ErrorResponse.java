package com.urlshortener.service.model;

import java.time.OffsetDateTime;

public record ErrorResponse(
        String error,
        String message,
        String timestamp
) {
    public static ErrorResponse of(String error, String message) {
        return new ErrorResponse(error, message, OffsetDateTime.now().toString());
    }
}

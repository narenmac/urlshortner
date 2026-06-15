package com.urlshortener.service.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record ShortenRequest(
        @NotBlank(message = "URL is required")
        @URL(message = "Must be a valid URL")
        @Size(max = 2048, message = "URL exceeds maximum length of 2048 characters")
        String url
) {}

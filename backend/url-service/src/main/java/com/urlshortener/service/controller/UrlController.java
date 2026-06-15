package com.urlshortener.service.controller;

import com.urlshortener.service.model.ErrorResponse;
import com.urlshortener.service.model.ShortenRequest;
import com.urlshortener.service.model.ShortenResponse;
import com.urlshortener.service.service.UrlShortenerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class UrlController {

    private final UrlShortenerService urlShortenerService;

    public UrlController(UrlShortenerService urlShortenerService) {
        this.urlShortenerService = urlShortenerService;
    }

    @PostMapping("/shorten")
    public ResponseEntity<?> shortenUrl(@Valid @RequestBody ShortenRequest request) {
        ShortenResponse response = urlShortenerService.shortenUrl(request.url());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/r/{code}")
    public ResponseEntity<?> redirect(@PathVariable String code) {
        if (code == null || !code.matches("^[a-zA-Z0-9]{6}$")) {
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.of("INVALID_CODE", "Code must be exactly 6 alphanumeric characters."));
        }

        String longUrl = urlShortenerService.resolveCode(code);
        if (longUrl == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorResponse.of("CODE_NOT_FOUND", "Short URL '" + code + "' does not exist."));
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(longUrl));
        headers.setCacheControl("no-cache");
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "services", Map.of("urlService", "UP", "tableStorage", "UP"),
                "timestamp", java.time.OffsetDateTime.now().toString()
        ));
    }
}

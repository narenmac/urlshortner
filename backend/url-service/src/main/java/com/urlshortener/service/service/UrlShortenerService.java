package com.urlshortener.service.service;

import com.google.common.hash.Hashing;
import com.urlshortener.service.model.ShortenResponse;
import com.urlshortener.service.repository.UrlRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

@Service
public class UrlShortenerService {

    private static final String BASE62_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private final UrlRepository urlRepository;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public UrlShortenerService(UrlRepository urlRepository) {
        this.urlRepository = urlRepository;
    }

    public ShortenResponse shortenUrl(String longUrl) {
        String code = generateCode(longUrl);

        // Check if URL already exists
        String existingUrl = urlRepository.findByCode(code);
        if (existingUrl != null && existingUrl.equals(longUrl)) {
            return new ShortenResponse(code, baseUrl + "/" + code, longUrl,
                    OffsetDateTime.now().toString(), false);
        }

        // Handle collision: different URL produces same hash
        if (existingUrl != null && !existingUrl.equals(longUrl)) {
            code = handleCollision(longUrl, code);
        }

        // Store in Azure Table
        urlRepository.save(code, longUrl);

        return new ShortenResponse(code, baseUrl + "/" + code, longUrl,
                OffsetDateTime.now().toString(), true);
    }

    public String resolveCode(String code) {
        return urlRepository.findByCode(code);
    }

    private String generateCode(String url) {
        long hash = Hashing.murmur3_128().hashString(url, StandardCharsets.UTF_8).asLong();
        return toBase62(Math.abs(hash), 6);
    }

    private String toBase62(long value, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(BASE62_CHARS.charAt((int) (value % 62)));
            value /= 62;
        }
        return sb.toString();
    }

    private String handleCollision(String longUrl, String originalCode) {
        for (int i = 1; i <= 10; i++) {
            String newCode = generateCode(longUrl + "#" + i);
            String existing = urlRepository.findByCode(newCode);
            if (existing == null || existing.equals(longUrl)) {
                return newCode;
            }
        }
        throw new RuntimeException("Unable to generate unique code after 10 attempts");
    }
}

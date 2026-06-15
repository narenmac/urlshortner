package com.urlshortener.service.service;

import com.google.common.hash.Hashing;
import com.urlshortener.service.model.ShortenResponse;
import com.urlshortener.service.repository.UrlRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;

@Service
public class UrlShortenerService {

    private static final String BASE62_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final String REDIS_KEY_PREFIX = "url:";
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private final UrlRepository urlRepository;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public UrlShortenerService(UrlRepository urlRepository, StringRedisTemplate redisTemplate) {
        this.urlRepository = urlRepository;
        this.redisTemplate = redisTemplate;
    }

    public ShortenResponse shortenUrl(String longUrl) {
        String code = generateCode(longUrl);

        // Check if URL already exists
        String existingUrl = getFromCache(code);
        if (existingUrl != null && existingUrl.equals(longUrl)) {
            return new ShortenResponse(code, baseUrl + "/" + code, longUrl,
                    OffsetDateTime.now().toString(), false);
        }

        // Handle collision: different URL produces same hash
        if (existingUrl != null && !existingUrl.equals(longUrl)) {
            code = handleCollision(longUrl, code);
        }

        // Store in Azure Table and cache
        urlRepository.save(code, longUrl);
        cacheUrl(code, longUrl);

        return new ShortenResponse(code, baseUrl + "/" + code, longUrl,
                OffsetDateTime.now().toString(), true);
    }

    public String resolveCode(String code) {
        // Try Redis cache first
        String cachedUrl = getFromCache(code);
        if (cachedUrl != null) {
            return cachedUrl;
        }

        // Fallback to Azure Table Storage
        String longUrl = urlRepository.findByCode(code);
        if (longUrl != null) {
            cacheUrl(code, longUrl);
        }
        return longUrl;
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
        // Append suffix and rehash
        for (int i = 1; i <= 10; i++) {
            String newCode = generateCode(longUrl + "#" + i);
            String existing = getFromCache(newCode);
            if (existing == null) {
                existing = urlRepository.findByCode(newCode);
            }
            if (existing == null || existing.equals(longUrl)) {
                return newCode;
            }
        }
        throw new RuntimeException("Unable to generate unique code after 10 attempts");
    }

    private void cacheUrl(String code, String longUrl) {
        try {
            redisTemplate.opsForValue().set(REDIS_KEY_PREFIX + code, longUrl, CACHE_TTL);
        } catch (Exception e) {
            // Cache write failure is non-critical
        }
    }

    private String getFromCache(String code) {
        try {
            return redisTemplate.opsForValue().get(REDIS_KEY_PREFIX + code);
        } catch (Exception e) {
            return null;
        }
    }
}

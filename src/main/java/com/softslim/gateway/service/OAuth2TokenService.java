package com.softslim.gateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class OAuth2TokenService {
    private static final long EXPIRY_SAFETY_WINDOW_SECONDS = 30;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, CachedToken> tokenCache = new ConcurrentHashMap<>();

    public OAuth2TokenService() {
        log.info("OAuth2TokenService inicializado");
    }

    public String getAccessToken(String tokenUri, String clientId, String clientSecret, String scope) {
        String cacheKey = buildCacheKey(tokenUri, clientId, scope);
        CachedToken cachedToken = tokenCache.get(cacheKey);

        if (cachedToken != null && cachedToken.expiresAt().isAfter(Instant.now())) {
            return cachedToken.token();
        }

        log.debug("Solicitando token OAuth2 para clientId={}", clientId);
        String token = requestClientCredentialsToken(tokenUri, clientId, clientSecret, scope);
        return token;
    }

    private String requestClientCredentialsToken(String tokenUri, String clientId, String clientSecret, String scope) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        if (scope != null && !scope.isBlank()) {
            form.add("scope", scope);
        }

        String response = restTemplate.postForObject(tokenUri, new HttpEntity<>(form, headers), String.class);
        if (response == null || response.isBlank()) {
            throw new IllegalStateException("Respuesta vacía de token OAuth2");
        }

        try {
            JsonNode tokenJson = objectMapper.readTree(response);
            String accessToken = tokenJson.path("access_token").asText(null);
            if (accessToken == null || accessToken.isBlank()) {
                throw new IllegalStateException("El token OAuth2 no contiene access_token");
            }

            long expiresIn = tokenJson.path("expires_in").asLong(300L);
            long safeTtl = Math.max(1L, expiresIn - EXPIRY_SAFETY_WINDOW_SECONDS);
            Instant expiresAt = Instant.now().plusSeconds(safeTtl);

            tokenCache.put(buildCacheKey(tokenUri, clientId, scope), new CachedToken(accessToken, expiresAt));
            return accessToken;
        } catch (Exception e) {
            throw new IllegalStateException("No fue posible parsear respuesta OAuth2", e);
        }
    }

    private String buildCacheKey(String tokenUri, String clientId, String scope) {
        return String.join("|",
            Objects.toString(tokenUri, ""),
            Objects.toString(clientId, ""),
            Objects.toString(scope, ""));
    }

    private record CachedToken(String token, Instant expiresAt) {
    }
}

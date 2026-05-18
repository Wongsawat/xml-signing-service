package com.wpanther.xmlsigning.infrastructure.config.feign;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Registers a Feign {@link RequestInterceptor} that adds an OAuth2 Bearer token
 * to every CSC API request. Only active for the {@code full-integration-test} profile.
 *
 * <p>The token is obtained via {@code POST /oauth2/token} with client_credentials grant
 * using the configured {@code app.csc.client-id} and {@code app.csc.client-secret}.
 * Tokens are cached and refreshed when they approach expiry.
 */
@Configuration
@Profile("full-integration-test")
@Slf4j
public class CscBearerTokenConfig {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${app.csc.service-url}")
    private String cscServiceUrl;

    @Value("${app.csc.oauth2.client-id}")
    private String clientId;

    @Value("${app.csc.oauth2.client-secret}")
    private String clientSecret;

    private volatile String cachedToken;
    private volatile Instant tokenExpiry = Instant.EPOCH;

    @PostConstruct
    void init() {
        log.info("CscBearerTokenConfig initialized for CSC API at {}, clientId={}",
                cscServiceUrl, clientId);
    }

    @Bean
    public RequestInterceptor cscBearerTokenInterceptor() {
        return (RequestTemplate requestTemplate) -> {
            String token = getValidToken();
            if (token != null && !token.isBlank()) {
                requestTemplate.header("Authorization", "Bearer " + token);
            } else {
                log.warn("No Bearer token available — CSC API calls will fail with 401");
            }
        };
    }

    private synchronized String getValidToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiry)) {
            return cachedToken;
        }
        return fetchNewToken();
    }

    private String fetchNewToken() {
        try {
            String credentials = clientId + ":" + clientSecret;
            String basicAuth = "Basic " + Base64.getEncoder()
                    .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(cscServiceUrl + "/oauth2/token"))
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "grant_type=client_credentials&scope=signing"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Authorization", basicAuth)
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("Failed to fetch OAuth2 token — HTTP {}: {}", response.statusCode(), response.body());
                return null;
            }

            JsonNode json = MAPPER.readTree(response.body());
            cachedToken = json.get("access_token").asText();
            long expiresIn = json.has("expires_in") ? json.get("expires_in").asLong() : 3600;
            tokenExpiry = Instant.now().plusSeconds(expiresIn - 60);
            log.info("Obtained new Bearer token, expires at {}", tokenExpiry);
            return cachedToken;

        } catch (Exception e) {
            log.error("Failed to fetch OAuth2 token from {}", cscServiceUrl, e);
            return null;
        }
    }
}

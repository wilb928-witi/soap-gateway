package com.softslim.gateway.service;

import com.softslim.gateway.exception.ApiInvocationException;
import com.softslim.gateway.model.BridgeConfiguration;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.apache.camel.Exchange;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.FileInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
public class RestInvocationService {
    private final Map<String, CircuitBreaker> circuitBreakerCache = new ConcurrentHashMap<>();
    private final Map<String, Retry> retryCache = new ConcurrentHashMap<>();

    public void invoke(Exchange exchange) {
        String targetUrl = exchange.getProperty("restTargetUrl", String.class);
        String methodName = exchange.getProperty("restMethod", String.class);
        Integer timeout = exchange.getProperty("restTimeout", Integer.class);
        @SuppressWarnings("unchecked")
        Map<String, String> outboundHeaders = exchange.getProperty("restOutboundHeaders", Map.class);
        BridgeConfiguration.Resilience resilience = exchange.getProperty("restResilience", BridgeConfiguration.Resilience.class);
        BridgeConfiguration.MutualTlsConfig mutualTls = exchange.getProperty("restMutualTls", BridgeConfiguration.MutualTlsConfig.class);
        String routeKey = exchange.getProperty("restRouteKey", String.class);

        if (targetUrl == null || methodName == null || timeout == null) {
            throw new IllegalArgumentException("Configuración REST incompleta para invocación");
        }

        applyMutualTlsIfEnabled(mutualTls);
        RestTemplate restTemplate = createRestTemplate(timeout);
        HttpMethod method = HttpMethod.valueOf(methodName);
        HttpHeaders headers = new HttpHeaders();
        if (outboundHeaders != null) {
            outboundHeaders.forEach(headers::add);
        }
        HttpEntity<?> entity = new HttpEntity<>(exchange.getIn().getBody(), headers);

        Supplier<ResponseEntity<String>> requestSupplier = () -> restTemplate.exchange(targetUrl, method, entity, String.class);
        requestSupplier = applyResilienceDecorators(routeKey, resilience, requestSupplier);

        try {
            ResponseEntity<String> response = requestSupplier.get();
            exchange.getIn().setBody(response.getBody() == null ? "{}" : response.getBody());
            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, response.getStatusCode().value());
            exchange.setProperty("apiResponseContentType", response.getHeaders().getFirst("Content-Type"));
        } catch (HttpStatusCodeException e) {
            throw new ApiInvocationException(
                e.getStatusCode().value(),
                e.getResponseBodyAsString(),
                e.getResponseHeaders() != null ? e.getResponseHeaders().getFirst("Content-Type") : null,
                e
            );
        } catch (CallNotPermittedException e) {
            throw ApiInvocationException.internal("Circuit breaker abierto para " + routeKey, e);
        } catch (RestClientException e) {
            throw ApiInvocationException.internal("Error invocando backend REST: " + e.getMessage(), e);
        }
    }

    private RestTemplate createRestTemplate(int timeoutMillis) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);
        return new RestTemplate(requestFactory);
    }

    private Supplier<ResponseEntity<String>> applyResilienceDecorators(
        String routeKey,
        BridgeConfiguration.Resilience resilience,
        Supplier<ResponseEntity<String>> supplier
    ) {
        Supplier<ResponseEntity<String>> decorated = supplier;
        if (resilience == null) {
            return decorated;
        }

        if (resilience.getCircuitBreaker() != null && resilience.getCircuitBreaker().isEnabled()) {
            CircuitBreaker circuitBreaker = circuitBreakerCache.computeIfAbsent(routeKey, key -> {
                BridgeConfiguration.CircuitBreakerConfig cfg = resilience.getCircuitBreaker();
                CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                    .failureRateThreshold(cfg.getFailureRateThreshold())
                    .waitDurationInOpenState(Duration.ofMillis(cfg.getWaitDurationInOpenState()))
                    .slidingWindowSize(cfg.getSlidingWindowSize())
                    .build();
                return CircuitBreaker.of(key, config);
            });
            decorated = CircuitBreaker.decorateSupplier(circuitBreaker, decorated);
        }

        if (resilience.getRetry() != null && resilience.getRetry().isEnabled()) {
            Retry retry = retryCache.computeIfAbsent(routeKey, key -> {
                BridgeConfiguration.RetryConfig cfg = resilience.getRetry();
                RetryConfig config = RetryConfig.custom()
                    .maxAttempts(cfg.getMaxAttempts())
                    .waitDuration(Duration.ofMillis(cfg.getBackoff()))
                    .retryExceptions(Exception.class)
                    .build();
                return Retry.of(key, config);
            });
            decorated = Retry.decorateSupplier(retry, decorated);
        }

        return decorated;
    }

    private void applyMutualTlsIfEnabled(BridgeConfiguration.MutualTlsConfig mutualTls) {
        if (mutualTls == null || !mutualTls.isEnabled()) {
            return;
        }

        String keyStorePath = requireValue(mutualTls.getKeystorePath(), "mutualTls.keystorePath");
        String keyStorePassword = requireValue(mutualTls.getKeystorePassword(), "mutualTls.keystorePassword");

        ensureFileReadable(keyStorePath, "mutualTls.keystorePath");
        System.setProperty("javax.net.ssl.keyStore", keyStorePath);
        System.setProperty("javax.net.ssl.keyStorePassword", keyStorePassword);

        if (mutualTls.getTruststorePath() != null && !mutualTls.getTruststorePath().isBlank()) {
            ensureFileReadable(mutualTls.getTruststorePath(), "mutualTls.truststorePath");
            System.setProperty("javax.net.ssl.trustStore", mutualTls.getTruststorePath());
            if (mutualTls.getTruststorePassword() != null) {
                System.setProperty("javax.net.ssl.trustStorePassword", mutualTls.getTruststorePassword());
            }
        }
    }

    private String requireValue(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Configuración faltante: " + key);
        }
        return value;
    }

    private void ensureFileReadable(String filePath, String key) {
        try (InputStream ignored = new FileInputStream(filePath)) {
            // validates file exists and is readable
        } catch (Exception e) {
            throw new IllegalArgumentException("Archivo no accesible para " + key + ": " + filePath, e);
        }
    }
}

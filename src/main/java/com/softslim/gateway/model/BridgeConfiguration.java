package com.softslim.gateway.model;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "bridge-protocols")
public class BridgeConfiguration {
    
    @NotNull
    private String version;
    
    @Valid
    private Map<String, EndpointClient> endpointsClients;

    private Resilience globalResilience;
    
    @Data
    public static class EndpointClient {
        private String version;
        private String soapPath;
        private Routing routing;
        private RestConfiguration rest;
        private Security security;
        private Resilience resilience;
    }
    
    @Data
    public static class Routing {
        private String strategy = "operation-name";
    }
    
    @Data
    public static class RestConfiguration {
        private String domainPath;
        private java.util.List<RestPath> paths;
    }
    
    @Data
    public static class RestPath {
        private String id;
        private String operation;
        private String path;
        private String method;
        private Integer timeout = 5000;
        private Map<String, String> headers;
        private Resilience resilience;
    }
    
    @Data
    public static class Security {
        private OAuth2Config oauth2;
        private WsSecurityConfig wsSecurity;
        private MutualTlsConfig mutualTls;
    }
    
    @Data
    public static class OAuth2Config {
        private boolean enabled = false;
        private String clientId;
        private String clientSecret;
        private String tokenUri;
        private String scope;
    }
    
    @Data
    public static class WsSecurityConfig {
        private boolean enabled = false;
        private String username;
        private String password;
    }
    
    @Data
    public static class MutualTlsConfig {
        private boolean enabled = false;
        private String keystorePath;
        private String keystorePassword;
        private String truststorePath;
        private String truststorePassword;
    }
    
    @Data
    public static class Resilience {
        private RetryConfig retry;
        private CircuitBreakerConfig circuitBreaker;
    }
    
    @Data
    public static class RetryConfig {
        private boolean enabled = false;
        private int maxAttempts = 3;
        private long backoff = 2000;
    }
    
    @Data
    public static class CircuitBreakerConfig {
        private boolean enabled = false;
        private int failureRateThreshold = 50;
        private long waitDurationInOpenState = 10000;
        private int slidingWindowSize = 10;
    }
}

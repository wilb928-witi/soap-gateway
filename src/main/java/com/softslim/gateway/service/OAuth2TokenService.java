package com.softslim.gateway.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class OAuth2TokenService {
    public OAuth2TokenService() {
        log.info("OAuth2TokenService inicializado");
    }

    public String getAccessToken(String clientRegistrationId) {
        log.debug("Solicitando token OAuth2 para: {}", clientRegistrationId);
        return "mock-token-" + clientRegistrationId;
    }
}

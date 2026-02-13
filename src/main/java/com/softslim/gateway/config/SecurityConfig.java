package com.softslim.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class SecurityConfig {
    public SecurityConfig() {
        log.info("Configuración de seguridad inicializada");
    }
}

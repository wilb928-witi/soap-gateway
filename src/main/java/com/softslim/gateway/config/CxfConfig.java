package com.softslim.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class CxfConfig {
    public CxfConfig() {
        log.info("Configuración CXF inicializada");
    }
}

package com.softslim.gateway.service;

import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class GlobalExceptionHandlerService {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandlerService.class);

    public String handle(Exception exception, Exchange exchange) {
        String correlationId = resolveCorrelationId(exchange);
        log.error("Error procesado por manejador global. correlationId={}", correlationId, exception);
        return correlationId;
    }

    public String handle(Exception exception, String correlationId) {
        String resolved = correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId;
        log.error("Error procesado por manejador global. correlationId={}", resolved, exception);
        return resolved;
    }

    private String resolveCorrelationId(Exchange exchange) {
        if (exchange == null) {
            return UUID.randomUUID().toString();
        }
        String correlationId = exchange.getIn().getHeader("CorrelationId", String.class);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
            exchange.getIn().setHeader("CorrelationId", correlationId);
        }
        return correlationId;
    }
}

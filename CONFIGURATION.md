# Guía de Configuración

## Configuración por Ambiente

### application-dev.yml

```yaml
server:
  port: 8080

logging:
  level:
    com.enterprise.gateway: DEBUG
    org.apache.camel: DEBUG

spring:
  security:
    oauth2:
      client:
        registration:
          backend-client:
            client-id: dev-client-id
            client-secret: dev-secret
            authorization-grant-type: client_credentials
            scope: api.read,api.write
        provider:
          backend-client:
            token-uri: http://localhost:8090/oauth2/token
```

### application-prod.yml

```yaml
server:
  port: 8080
  ssl:
    enabled: true
    key-store: classpath:keystore.jks
    key-store-password: ${KEYSTORE_PASSWORD}
    key-store-type: JKS

logging:
  level:
    com.enterprise.gateway: INFO
    org.apache.camel: WARN

spring:
  security:
    oauth2:
      client:
        registration:
          backend-client:
            client-id: ${OAUTH2_CLIENT_ID}
            client-secret: ${OAUTH2_CLIENT_SECRET}
            authorization-grant-type: client_credentials
            scope: api.read,api.write
        provider:
          backend-client:
            token-uri: ${OAUTH2_TOKEN_URI}
```

## Variables de Entorno

Configurar las siguientes variables:

```bash
# OAuth2
export OAUTH2_CLIENT_ID=your-client-id
export OAUTH2_CLIENT_SECRET=your-client-secret
export OAUTH2_TOKEN_URI=https://oauth-server/token

# WS-Security
export WS_SECURITY_USERNAME=ws-user
export WS_SECURITY_PASSWORD=ws-password

# TLS
export KEYSTORE_PASSWORD=keystore-pass
export TRUSTSTORE_PASSWORD=truststore-pass
```

## Configuración de Logging

### Logging estructurado JSON (opcional)

Agregar dependencia:

```xml
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>7.4</version>
</dependency>
```

### logback-spring.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <fieldNames>
                <timestamp>timestamp</timestamp>
                <message>message</message>
                <logger>logger</logger>
                <level>level</level>
            </fieldNames>
        </encoder>
    </appender>
    
    <root level="INFO">
        <appender-ref ref="JSON"/>
    </root>
</configuration>
```

## Configuración de Resilience4j

### application.yml

```yaml
resilience4j:
  retry:
    instances:
      default:
        max-attempts: 3
        wait-duration: 2000
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
  
  circuitbreaker:
    instances:
      default:
        failure-rate-threshold: 50
        slow-call-rate-threshold: 100
        slow-call-duration-threshold: 5000
        permitted-number-of-calls-in-half-open-state: 3
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10
        minimum-number-of-calls: 5
        wait-duration-in-open-state: 10000
        automatic-transition-from-open-to-half-open-enabled: true
```

## Configuración de Métricas

### Activar métricas personalizadas

```yaml
management:
  metrics:
    tags:
      application: soap-gateway
      environment: ${ENVIRONMENT:dev}
    export:
      prometheus:
        enabled: true
        step: 1m
```

## Configuración de TLS/SSL

### Generar Keystore

```bash
keytool -genkeypair -alias soap-gateway \
  -keyalg RSA -keysize 2048 \
  -storetype JKS \
  -keystore keystore.jks \
  -validity 3650 \
  -storepass changeit
```

### Generar Truststore

```bash
keytool -import -alias backend-cert \
  -file backend.crt \
  -keystore truststore.jks \
  -storepass changeit
```

## Troubleshooting

### Error: No se puede conectar al backend REST

```yaml
# Verificar configuración
rest:
  domain-path: http://localhost:8081  # ¿Es correcto?
  paths:
    - path: /api/endpoint  # ¿Path correcto?
```

### Error: OAuth2 token no se obtiene

```bash
# Verificar conectividad
curl -X POST http://oauth-server/token \
  -d "grant_type=client_credentials" \
  -d "client_id=your-client-id" \
  -d "client_secret=your-secret"
```

### Error: Circuit Breaker siempre abierto

```yaml
# Ajustar umbrales
circuitBreaker:
  failureRateThreshold: 80  # Aumentar tolerancia
  slidingWindowSize: 20     # Más muestras
```

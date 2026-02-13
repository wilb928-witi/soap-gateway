# SOAP Gateway - Motor Declarativo de Bridge SOAP ↔ REST

Motor empresarial de integración que permite exponer servicios SOAP y enrutar peticiones a múltiples endpoints REST de forma 100% declarativa mediante YAML.

## 🎯 Características Principales

- ✅ **Bridge SOAP → REST** completamente declarativo
- ✅ **Multi-endpoint** por contrato SOAP
- ✅ **Sin cambios de código** al agregar endpoints
- ✅ **Seguridad multicapa**: OAuth2, WS-Security, Mutual TLS
- ✅ **Resiliencia**: Retry y Circuit Breaker configurables
- ✅ **Observabilidad**: Métricas Prometheus, trazas distribuidas
- ✅ **Conversión automática** JSON ↔ XML
- ✅ **Gestión de errores** con SOAP Faults estándar

## 🏗️ Arquitectura

```
Cliente SOAP
    ↓
CXF Endpoint (SOAP)
    ↓
Motor Declarativo (Apache Camel)
    ↓
Router Dinámico
    ↓
Políticas de Seguridad/Resiliencia
    ↓
REST Backend
    ↓
Respuesta JSON → XML
```

## 📋 Requisitos

- Java 17+
- Maven 3.8+
- Spring Boot 3.2+
- Apache Camel 4.3+
- Apache CXF 4.0+

## 🚀 Inicio Rápido

### 1. Clonar el repositorio

```bash
cd D:\repositories\e-softslim02\soap-gateway
```

### 2. Configurar el archivo YAML

Editar `src/main/resources/bridge-protocols.yml`:

```yaml
bridge-protocols:
  version: "1.0"
  
  endpoints-clients:
    miServicio:
      version: v1
      soap-path: /soap/miServicio
      routing:
        strategy: operation-name
      
      rest:
        domain-path: http://localhost:8081/api
        paths:
          - id: operacion1
            operation: getRecurso
            path: /recursos/${header.id}
            method: GET
            timeout: 5000
```

### 3. Compilar

```bash
mvn clean package
```

### 4. Ejecutar

```bash
mvn spring-boot:run
```

O ejecutar el JAR:

```bash
java -jar target/soap-gateway-1.0.0.jar
```

## 📝 Configuración YAML

### Estructura Completa

```yaml
bridge-protocols:
  version: "1.0"
  
  endpoints-clients:
    nombreServicio:
      version: v1
      soap-path: /soap/path
      
      routing:
        strategy: operation-name  # operation-name | header-based
      
      rest:
        domain-path: http://backend-host/api
        paths:
          - id: unique-id
            operation: soapOperationName
            path: /rest/path/${header.param}
            method: GET|POST|PUT|DELETE
            timeout: 5000
            headers:
              Content-Type: application/json
              Custom-Header: value
      
      security:
        oauth2:
          enabled: true
          client-id: client-id
          client-secret: ${SECRET}
          token-uri: http://oauth-server/token
          scope: scope1 scope2
        
        wsSecurity:
          enabled: true
          username: user
          password: ${PASSWORD}
          signatureEnabled: true
          encryptionEnabled: false
        
        mutualTls:
          enabled: true
          keystorePath: /path/to/keystore.jks
          keystorePassword: ${KEYSTORE_PASS}
          truststorePath: /path/to/truststore.jks
          truststorePassword: ${TRUSTSTORE_PASS}
      
      resilience:
        retry:
          enabled: true
          maxAttempts: 3
          backoff: 2000
        
        circuitBreaker:
          enabled: true
          failureRateThreshold: 50
          waitDurationInOpenState: 10000
          slidingWindowSize: 10
```

## 🔒 Seguridad

### OAuth2 Client Credentials

El motor gestiona automáticamente:
- Obtención de tokens
- Cache de tokens
- Renovación automática antes de expiración
- Inyección en headers HTTP

### WS-Security

Soporte para:
- UsernameToken
- Firma XML (XML Signature)
- Encriptación (XML Encryption)
- Timestamp validation

### Mutual TLS

- Validación de certificados cliente
- Configuración por entorno
- Keystore y Truststore personalizables

## 🛡️ Resiliencia

### Retry Policy

```yaml
retry:
  enabled: true
  maxAttempts: 3      # Número de reintentos
  backoff: 2000       # Tiempo entre reintentos (ms)
```

### Circuit Breaker

```yaml
circuitBreaker:
  enabled: true
  failureRateThreshold: 50        # % de fallos para abrir circuito
  waitDurationInOpenState: 10000  # Tiempo en estado abierto (ms)
  slidingWindowSize: 10           # Ventana de medición
```

## 📊 Observabilidad

### Métricas Prometheus

Expuestas en: `http://localhost:8080/actuator/prometheus`

Métricas disponibles:
- `camel_exchanges_total`
- `camel_exchanges_failed_total`
- `http_requests_duration_seconds`
- `soap_requests_total`
- `rest_calls_total`

### Health Check

```bash
curl http://localhost:8080/actuator/health
```

### Correlation ID

Cada petición genera un `CorrelationId` único para trazabilidad:

```xml
<detail>
  <correlationId>ABC-123-XYZ</correlationId>
</detail>
```

## 🧪 Ejemplo de Uso

### Petición SOAP

```xml
POST /soap/clienteService
Content-Type: text/xml

<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                  xmlns:cli="http://enterprise.com/gateway/clienteService">
   <soapenv:Header/>
   <soapenv:Body>
      <cli:getCliente>
         <clienteId>12345</clienteId>
      </cli:getCliente>
   </soapenv:Body>
</soapenv:Envelope>
```

### Flujo Interno

1. CXF recibe petición SOAP
2. Motor extrae operación: `getCliente`
3. Busca configuración para operación
4. Obtiene token OAuth2 (si habilitado)
5. Construye URL REST: `http://localhost:8081/api/clientes/endpoint1/12345`
6. Aplica retry/circuit breaker
7. Invoca endpoint REST
8. Convierte JSON a XML
9. Devuelve respuesta SOAP

### Respuesta SOAP

```xml
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
  <soap:Body>
    <ns:getClienteResponse xmlns:ns="http://enterprise.com/gateway/clienteService">
      <result>
        {"id": "12345", "nombre": "Juan Pérez", "email": "juan@example.com"}
      </result>
    </ns:getClienteResponse>
  </soap:Body>
</soap:Envelope>
```

## 🔧 Manejo de Errores

### SOAP Fault en caso de error

```xml
<soap:Fault>
  <faultcode>soap:Server.Timeout</faultcode>
  <faultstring>Connection timeout</faultstring>
  <detail>
    <correlationId>ABC-123-XYZ</correlationId>
  </detail>
</soap:Fault>
```

### Códigos de Fault

- `Server.Timeout` - Timeout en llamada REST
- `Server.ConnectionError` - Error de conexión
- `Server.InternalError` - Error interno del motor

## 📦 Estructura del Proyecto

```
soap-gateway/
├── src/
│   ├── main/
│   │   ├── java/com/enterprise/gateway/
│   │   │   ├── SoapGatewayApplication.java
│   │   │   ├── config/
│   │   │   │   ├── CamelConfig.java
│   │   │   │   └── CxfConfig.java
│   │   │   ├── model/
│   │   │   │   ├── BridgeProtocolsConfig.java
│   │   │   │   ├── ServiceEndpointConfig.java
│   │   │   │   └── ... (otros modelos)
│   │   │   ├── routes/
│   │   │   │   └── DynamicRouteBuilder.java
│   │   │   ├── processor/
│   │   │   │   ├── RestResponseProcessor.java
│   │   │   │   └── SoapFaultProcessor.java
│   │   │   └── security/
│   │   │       └── OAuth2TokenManager.java
│   │   └── resources/
│   │       ├── application.yml
│   │       └── bridge-protocols.yml
│   └── test/
└── pom.xml
```

## 🚦 Estado del Proyecto

- ✅ Bridge SOAP → REST básico
- ✅ Configuración declarativa YAML
- ✅ Enrutamiento dinámico
- ✅ OAuth2 Client Credentials
- ✅ Retry y Circuit Breaker
- ✅ Observabilidad básica
- ⚠️ WS-Security (en desarrollo)
- ⚠️ Mutual TLS (en desarrollo)
- 📋 Hot reload de configuración (pendiente)

## 🤝 Contribución

Para agregar nuevos servicios:

1. Editar `bridge-protocols.yml`
2. Reiniciar aplicación
3. ¡Listo! No se requieren cambios de código

## 📄 Licencia

Uso interno empresarial.

## 📞 Soporte

Para consultas técnicas, contactar al equipo de arquitectura empresarial.

---

**Versión:** 1.0.0  
**Última actualización:** 2024

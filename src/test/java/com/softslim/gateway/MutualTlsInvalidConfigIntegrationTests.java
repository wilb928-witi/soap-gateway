package com.softslim.gateway;

import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MutualTlsInvalidConfigIntegrationTests {
    private static MockWebServer backendServer;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeAll
    static void beforeAll() throws Exception {
        backendServer = new MockWebServer();
        backendServer.start();
    }

    @AfterAll
    static void afterAll() throws Exception {
        if (backendServer != null) {
            backendServer.shutdown();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("bridge-protocols.endpoints-clients.clienteService.rest.domain-path",
            () -> backendServer.url("/api/clientes").toString());
        registry.add("bridge-protocols.endpoints-clients.clienteService.security.oauth2.enabled", () -> false);
        registry.add("bridge-protocols.endpoints-clients.clienteService.security.ws-security.enabled", () -> false);
        registry.add("bridge-protocols.endpoints-clients.clienteService.security.mutual-tls.enabled", () -> true);
        registry.add("bridge-protocols.endpoints-clients.clienteService.security.mutual-tls.keystore-path", () -> "");
        registry.add("camel.servlet.servlet-name", () -> "CamelServletMutualTlsInvalidTest");
    }

    @Test
    void shouldReturnSoapFaultWhenMutualTlsEnabledWithoutKeystorePath() {
        String soapRequest =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:cli=\"http://softslim.com/gateway/clienteService\">" +
            "<soapenv:Header/>" +
            "<soapenv:Body><cli:getCliente><clienteId>200</clienteId></cli:getCliente></soapenv:Body>" +
            "</soapenv:Envelope>";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_XML);
        ResponseEntity<String> response = restTemplate.postForEntity(
            "http://localhost:" + port + "/soap/clienteService",
            new HttpEntity<>(soapRequest, headers),
            String.class);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertTrue(response.getBody().contains("Configuración faltante: mutualTls.keystorePath"));
    }
}

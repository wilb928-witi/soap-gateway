package com.softslim.gateway;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SoapGatewayApplicationTests {
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
        registry.add("bridge-protocols.endpoints-clients.empleadoService.security.oauth2.enabled", () -> false);
        registry.add("camel.servlet.servlet-name", () -> "CamelServletSoapGatewayTest");
    }

    @Test
    void contextLoads() {
    }

    @Test
    void shouldExposeSoapAndBridgeToRest() throws Exception {
        backendServer.enqueue(new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("{\"id\":\"12345\",\"nombre\":\"Juan\"}"));

        String soapRequest =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
            "xmlns:cli=\"http://softslim.com/gateway/clienteService\">" +
            "<soapenv:Header/>" +
            "<soapenv:Body>" +
            "<cli:getCliente>" +
            "<clienteId>12345</clienteId>" +
            "<header><channel>MOBILE</channel></header>" +
            "</cli:getCliente>" +
            "</soapenv:Body>" +
            "</soapenv:Envelope>";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_XML);
        ResponseEntity<String> response = restTemplate.postForEntity(
            "http://localhost:" + port + "/soap/clienteService",
            new HttpEntity<>(soapRequest, headers),
            String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("getClienteResponse"));
        assertTrue(response.getBody().contains("<success>true</success>"));
        assertTrue(response.getBody().contains("<statusCode>200</statusCode>"));
        assertTrue(response.getBody().contains("<dataRedeable>true</dataRedeable>"));
        assertTrue(response.getBody().contains("<id>12345</id>"));

        RecordedRequest recordedRequest = backendServer.takeRequest();
        assertEquals("GET", recordedRequest.getMethod());
        assertTrue(recordedRequest.getPath().startsWith("/api/clientes/endpoint1/12345"));
        assertEquals("MOBILE", recordedRequest.getHeader("X-Channel"));
    }

    @Test
    void shouldExposeWsdlFromEnvironmentConfiguration() {
        ResponseEntity<String> wsdlResponse = restTemplate.getForEntity(
            "http://localhost:" + port + "/soap/clienteService?wsdl",
            String.class);

        assertEquals(HttpStatus.OK, wsdlResponse.getStatusCode());
        assertNotNull(wsdlResponse.getBody());
        assertTrue(wsdlResponse.getBody().contains("wsdl:definitions"));
        assertTrue(wsdlResponse.getBody().contains("name=\"getCliente\""));
        assertTrue(wsdlResponse.getBody().contains("soap:address"));
    }

    @Test
    void shouldRetryRestCallWhenConfigured() throws Exception {
        int initialCount = backendServer.getRequestCount();
        backendServer.enqueue(new MockResponse().setResponseCode(500).setBody("{\"error\":\"1\"}"));
        backendServer.enqueue(new MockResponse().setResponseCode(500).setBody("{\"error\":\"2\"}"));
        backendServer.enqueue(new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("{\"id\":\"12345\",\"nombre\":\"Juan\"}"));

        String soapRequest =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
            "xmlns:cli=\"http://softslim.com/gateway/clienteService\">" +
            "<soapenv:Header/>" +
            "<soapenv:Body>" +
            "<cli:getCliente><clienteId>12345</clienteId><header><channel>MOBILE</channel></header></cli:getCliente>" +
            "</soapenv:Body>" +
            "</soapenv:Envelope>";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_XML);
        ResponseEntity<String> response = restTemplate.postForEntity(
            "http://localhost:" + port + "/soap/clienteService",
            new HttpEntity<>(soapRequest, headers),
            String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("<success>true</success>"));
        assertTrue(response.getBody().contains("<statusCode>200</statusCode>"));
        assertTrue(response.getBody().contains("<dataRedeable>true</dataRedeable>"));
        assertTrue(response.getBody().contains("<id>12345</id>"));

        backendServer.takeRequest();
        backendServer.takeRequest();
        backendServer.takeRequest();
        assertEquals(initialCount + 3, backendServer.getRequestCount());
    }

    @Test
    void shouldReturnValidSoapFaultForDisallowedDoctype() throws Exception {
        String maliciousSoap =
            "<?xml version=\"1.0\"?>" +
            "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>" +
            "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:cli=\"http://softslim.com/gateway/clienteService\">" +
            "<soapenv:Body><cli:getCliente><clienteId>&xxe;</clienteId><header><channel>MOBILE</channel></header></cli:getCliente></soapenv:Body>" +
            "</soapenv:Envelope>";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_XML);
        ResponseEntity<String> response = restTemplate.postForEntity(
            "http://localhost:" + port + "/soap/clienteService",
            new HttpEntity<>(maliciousSoap, headers),
            String.class);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("<success>false</success>"));
        assertTrue(response.getBody().contains("<statusCode>0</statusCode>"));

        DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(new InputSource(new StringReader(response.getBody())));
    }
}

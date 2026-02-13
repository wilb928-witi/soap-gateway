package com.softslim.gateway.routes;

import com.softslim.gateway.model.BridgeConfiguration;
import com.softslim.gateway.processor.JsonToXmlProcessor;
import com.softslim.gateway.processor.SoapFaultProcessor;
import com.softslim.gateway.service.OAuth2TokenService;
import com.softslim.gateway.service.RestInvocationService;
import com.softslim.gateway.service.WsdlContractService;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class DynamicBridgeRouteBuilder extends RouteBuilder {
    private static final Pattern HEADER_PLACEHOLDER = Pattern.compile("\\$\\{header\\.([^}]+)}");

    private final BridgeConfiguration bridgeConfig;
    private final JsonToXmlProcessor jsonToXmlProcessor;
    private final SoapFaultProcessor soapFaultProcessor;
    private final OAuth2TokenService oAuth2TokenService;
    private final WsdlContractService wsdlContractService;
    private final RestInvocationService restInvocationService;

    public DynamicBridgeRouteBuilder(BridgeConfiguration bridgeConfig,
                                      JsonToXmlProcessor jsonToXmlProcessor,
                                      SoapFaultProcessor soapFaultProcessor,
                                      OAuth2TokenService oAuth2TokenService,
                                      WsdlContractService wsdlContractService,
                                      RestInvocationService restInvocationService) {
        this.bridgeConfig = bridgeConfig;
        this.jsonToXmlProcessor = jsonToXmlProcessor;
        this.soapFaultProcessor = soapFaultProcessor;
        this.oAuth2TokenService = oAuth2TokenService;
        this.wsdlContractService = wsdlContractService;
        this.restInvocationService = restInvocationService;
    }

    @Override
    public void configure() throws Exception {
        
        log.info("Configurando rutas dinámicas del gateway");
        
        onException(Exception.class)
            .handled(true)
            .process(soapFaultProcessor)
            .log("Error procesado");

        if (bridgeConfig.getEndpointsClients() != null && !bridgeConfig.getEndpointsClients().isEmpty()) {
            bridgeConfig.getEndpointsClients().forEach((serviceName, endpointClient) -> {
                try {
                    createServiceRoutes(serviceName, endpointClient);
                } catch (Exception e) {
                    log.error("Error creando rutas para servicio: {}", serviceName, e);
                }
            });
        }
        
        log.info("Rutas dinámicas configuradas exitosamente");
    }

    private void createServiceRoutes(String serviceName, BridgeConfiguration.EndpointClient endpointClient) throws Exception {
        log.info("Creando rutas para servicio: {}", serviceName);
        String soapPath = normalizeSoapPath(endpointClient.getSoapPath(), serviceName);
        String dispatchRouteId = "dispatch-" + serviceName;
        String internalSoapEntryRouteId = "soap-internal-" + serviceName;
        String wsdlRouteId = "wsdl-" + serviceName;

        from("servlet:" + soapPath + "?httpMethodRestrict=GET")
            .routeId(wsdlRouteId)
            .process(exchange -> buildWsdlResponse(exchange, serviceName, endpointClient, soapPath));

        from("servlet:" + soapPath + "?httpMethodRestrict=POST")
            .routeId("soap-in-" + serviceName)
            .convertBodyTo(String.class)
            .to("direct:" + internalSoapEntryRouteId);

        from("direct:" + internalSoapEntryRouteId)
            .routeId(internalSoapEntryRouteId)
            .process(exchange -> extractSoapContext(exchange, endpointClient))
            .to("direct:" + dispatchRouteId);

        var dispatchChoice = from("direct:" + dispatchRouteId)
            .routeId(dispatchRouteId)
            .choice();

        if (endpointClient.getRest() != null && endpointClient.getRest().getPaths() != null) {
            endpointClient.getRest().getPaths().forEach(restPath -> {
                try {
                    dispatchChoice
                        .when(header("SoapOperation").isEqualTo(restPath.getOperation()))
                        .to("direct:operation-" + serviceName + "-" + restPath.getOperation());
                    createOperationRoute(serviceName, endpointClient, restPath);
                } catch (Exception e) {
                    log.error("Error creando ruta para operación: {}", restPath.getOperation(), e);
                }
            });
        }

        dispatchChoice
            .otherwise()
            .throwException(new IllegalArgumentException(
                "Operación SOAP no soportada para servicio " + serviceName + ": ${header.SoapOperation}"))
            .end();
    }

    private void createOperationRoute(String serviceName, 
                                       BridgeConfiguration.EndpointClient endpointClient,
                                       BridgeConfiguration.RestPath restPath) throws Exception {
        
        String routeId = "operation-" + serviceName + "-" + restPath.getOperation();
        
        log.info("Creando ruta: {}", routeId);

        from("direct:" + routeId)
            .routeId(routeId)
            .log("Ejecutando operación: " + restPath.getOperation())
            .process(exchange -> prepareRestInvocation(exchange, endpointClient, restPath))
            .process(restInvocationService::invoke)
            .convertBodyTo(String.class)
            .process(jsonToXmlProcessor)
            .process(this::buildSoapSuccessResponse)
            .log("Respuesta SOAP generada para operación: " + restPath.getOperation());
    }

    private void prepareRestInvocation(
        Exchange exchange,
        BridgeConfiguration.EndpointClient endpointClient,
        BridgeConfiguration.RestPath restPath
    ) throws Exception {
        String domainPath = endpointClient.getRest() != null ? endpointClient.getRest().getDomainPath() : null;
        if (domainPath == null || domainPath.isBlank()) {
            throw new IllegalArgumentException("domain-path no configurado para la operación " + restPath.getOperation());
        }

        String resolvedPath = resolvePathVariables(restPath.getPath(), exchange);
        String targetUrl = buildTargetUrl(domainPath, resolvedPath);
        exchange.setProperty("restTargetUrl", targetUrl);
        exchange.setProperty("restRouteKey", endpointClient.getSoapPath() + "#" + restPath.getOperation());

        String method = restPath.getMethod() == null ? "GET" : restPath.getMethod().toUpperCase(Locale.ROOT);
        exchange.setProperty("restMethod", method);
        exchange.setProperty("restTimeout", restPath.getTimeout() == null ? 5000 : restPath.getTimeout());

        Map<String, String> outboundHeaders = new LinkedHashMap<>();

        if (restPath.getHeaders() != null) {
            outboundHeaders.putAll(restPath.getHeaders());
        }

        applySecurity(exchange, endpointClient, outboundHeaders);
        exchange.setProperty("restOutboundHeaders", outboundHeaders);
        exchange.setProperty("restResilience", resolveResilience(endpointClient, restPath));
        exchange.setProperty("restMutualTls", endpointClient.getSecurity() != null ? endpointClient.getSecurity().getMutualTls() : null);

        if (isBodyMethod(method)) {
            Object paramsObject = exchange.getProperty("SoapParameters");
            @SuppressWarnings("unchecked")
            Map<String, String> params = paramsObject instanceof Map ? (Map<String, String>) paramsObject : Map.of();
            String requestBody = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(params);
            exchange.getIn().setBody(requestBody);
            exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/json");
        } else {
            exchange.getIn().setBody(null);
        }
    }

    private void applySecurity(
        Exchange exchange,
        BridgeConfiguration.EndpointClient endpointClient,
        Map<String, String> outboundHeaders
    ) {
        if (endpointClient.getSecurity() == null || endpointClient.getSecurity().getOauth2() == null) {
            return;
        }

        BridgeConfiguration.OAuth2Config oauth2 = endpointClient.getSecurity().getOauth2();
        if (!oauth2.isEnabled()) {
            return;
        }

        if (oauth2.getTokenUri() == null || oauth2.getClientId() == null || oauth2.getClientSecret() == null) {
            throw new IllegalArgumentException("Configuración OAuth2 incompleta para servicio SOAP");
        }

        String token = oAuth2TokenService.getAccessToken(
            oauth2.getTokenUri(),
            oauth2.getClientId(),
            oauth2.getClientSecret(),
            oauth2.getScope()
        );
        outboundHeaders.put("Authorization", "Bearer " + token);
    }

    private BridgeConfiguration.Resilience resolveResilience(
        BridgeConfiguration.EndpointClient endpointClient,
        BridgeConfiguration.RestPath restPath
    ) {
        if (restPath.getResilience() != null) {
            return restPath.getResilience();
        }
        if (endpointClient.getResilience() != null) {
            return endpointClient.getResilience();
        }
        return bridgeConfig.getGlobalResilience();
    }

    private void extractSoapContext(Exchange exchange, BridgeConfiguration.EndpointClient endpointClient) throws Exception {
        String soapRequest = exchange.getIn().getBody(String.class);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(soapRequest)));
        validateWsSecurity(document, endpointClient);

        Element body = findElementByLocalName(document.getDocumentElement(), "Body");
        if (body == null) {
            throw new IllegalArgumentException("SOAP Body no encontrado");
        }

        Element operationElement = firstChildElement(body);
        if (operationElement == null) {
            throw new IllegalArgumentException("No se encontró operación dentro del SOAP Body");
        }

        String operationName = operationElement.getLocalName() != null
            ? operationElement.getLocalName()
            : operationElement.getNodeName();

        exchange.getIn().setHeader("SoapOperation", operationName);
        exchange.getIn().setHeader("SoapNamespace", operationElement.getNamespaceURI());

        Map<String, String> parameters = new HashMap<>();
        NodeList children = operationElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            String key = child.getLocalName() != null ? child.getLocalName() : child.getNodeName();
            String value = child.getTextContent();
            parameters.put(key, value);
            exchange.getIn().setHeader(key, value);
        }

        exchange.setProperty("SoapParameters", parameters);
    }

    private void validateWsSecurity(Document document, BridgeConfiguration.EndpointClient endpointClient) {
        if (endpointClient.getSecurity() == null || endpointClient.getSecurity().getWsSecurity() == null) {
            return;
        }

        BridgeConfiguration.WsSecurityConfig wsSecurity = endpointClient.getSecurity().getWsSecurity();
        if (!wsSecurity.isEnabled()) {
            return;
        }

        if (wsSecurity.getUsername() == null || wsSecurity.getPassword() == null) {
            throw new IllegalArgumentException("Configuración WS-Security incompleta");
        }

        Element header = findElementByLocalName(document.getDocumentElement(), "Header");
        if (header == null) {
            throw new IllegalArgumentException("SOAP Header requerido para WS-Security");
        }

        Element usernameToken = findElementByLocalName(header, "UsernameToken");
        if (usernameToken == null) {
            throw new IllegalArgumentException("WS-Security UsernameToken requerido");
        }

        Element username = findElementByLocalName(usernameToken, "Username");
        Element password = findElementByLocalName(usernameToken, "Password");
        if (username == null || password == null) {
            throw new IllegalArgumentException("WS-Security Username/Password requeridos");
        }

        String providedUsername = username.getTextContent();
        String providedPassword = password.getTextContent();
        if (!wsSecurity.getUsername().equals(providedUsername) || !wsSecurity.getPassword().equals(providedPassword)) {
            throw new IllegalArgumentException("Credenciales WS-Security inválidas");
        }
    }

    private void buildSoapSuccessResponse(Exchange exchange) {
        String xmlFragment = exchange.getIn().getBody(String.class);
        String operationName = exchange.getIn().getHeader("SoapOperation", String.class);
        String namespace = exchange.getIn().getHeader("SoapNamespace", String.class);
        if (namespace == null || namespace.isBlank()) {
            namespace = "http://softslim.com/gateway";
        }

        String soapResponse =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
            "  <soap:Body>" +
            "    <ns:" + operationName + "Response xmlns:ns=\"" + escapeXml(namespace) + "\">" +
            xmlFragment +
            "    </ns:" + operationName + "Response>" +
            "  </soap:Body>" +
            "</soap:Envelope>";

        exchange.getIn().setBody(soapResponse);
        exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "text/xml");
    }

    private void buildWsdlResponse(
        Exchange exchange,
        String serviceName,
        BridgeConfiguration.EndpointClient endpointClient,
        String soapPath
    ) {
        String query = exchange.getIn().getHeader(Exchange.HTTP_QUERY, String.class);
        if (query == null || !query.toLowerCase(Locale.ROOT).contains("wsdl")) {
            exchange.getIn().setBody("WSDL request expected");
            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
            return;
        }

        String endpointUrl = exchange.getIn().getHeader(Exchange.HTTP_URI, String.class);
        if (endpointUrl == null || endpointUrl.isBlank()) {
            endpointUrl = soapPath;
        }

        String wsdl = wsdlContractService.buildWsdl(serviceName, endpointClient, endpointUrl);
        exchange.getIn().setBody(wsdl);
        exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "text/xml");
    }

    private String normalizeSoapPath(String soapPath, String serviceName) {
        if (soapPath == null || soapPath.isBlank()) {
            return "/soap/" + serviceName;
        }

        return soapPath.startsWith("/") ? soapPath : "/" + soapPath;
    }

    private String resolvePathVariables(String path, Exchange exchange) {
        if (path == null || path.isBlank()) {
            return "";
        }

        Matcher matcher = HEADER_PLACEHOLDER.matcher(path);
        StringBuilder resolved = new StringBuilder();
        while (matcher.find()) {
            String headerName = matcher.group(1);
            Object value = exchange.getIn().getHeader(headerName);
            if (value == null) {
                throw new IllegalArgumentException("Header requerido no encontrado para path REST: " + headerName);
            }
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(value.toString()));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }

    private String buildTargetUrl(String domainPath, String path) {
        String base = domainPath.endsWith("/") ? domainPath.substring(0, domainPath.length() - 1) : domainPath;
        String suffix = path.startsWith("/") ? path : "/" + path;
        return base + suffix;
    }

    private boolean isBodyMethod(String method) {
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
    }

    private Element findElementByLocalName(Element start, String localName) {
        if (start == null) {
            return null;
        }

        if (localName.equals(start.getLocalName()) || localName.equals(start.getNodeName())) {
            return start;
        }

        NodeList children = start.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element found = findElementByLocalName((Element) child, localName);
            if (found != null) {
                return found;
            }
        }

        return null;
    }

    private Element firstChildElement(Element parent) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                return (Element) child;
            }
        }
        return null;
    }

    private String escapeXml(String value) {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }
}

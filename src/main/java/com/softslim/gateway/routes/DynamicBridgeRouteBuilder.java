package com.softslim.gateway.routes;

import com.softslim.gateway.model.BridgeConfiguration;
import com.softslim.gateway.processor.JsonToXmlProcessor;
import com.softslim.gateway.processor.SoapFaultProcessor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DynamicBridgeRouteBuilder extends RouteBuilder {

    private final BridgeConfiguration bridgeConfig;
    private final JsonToXmlProcessor jsonToXmlProcessor;
    private final SoapFaultProcessor soapFaultProcessor;

    public DynamicBridgeRouteBuilder(BridgeConfiguration bridgeConfig,
                                      JsonToXmlProcessor jsonToXmlProcessor,
                                      SoapFaultProcessor soapFaultProcessor) {
        this.bridgeConfig = bridgeConfig;
        this.jsonToXmlProcessor = jsonToXmlProcessor;
        this.soapFaultProcessor = soapFaultProcessor;
    }

    @Override
    public void configure() throws Exception {
        
        log.info("Configurando rutas dinámicas del gateway");
        
        onException(Exception.class)
            .handled(true)
            .process(soapFaultProcessor)
            .log("Error procesado");

        if (bridgeConfig.getEndpointsClients() != null) {
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

        if (endpointClient.getRest() != null && endpointClient.getRest().getPaths() != null) {
            endpointClient.getRest().getPaths().forEach(restPath -> {
                try {
                    createOperationRoute(serviceName, endpointClient, restPath);
                } catch (Exception e) {
                    log.error("Error creando ruta para operación: {}", restPath.getOperation(), e);
                }
            });
        }
    }

    private void createOperationRoute(String serviceName, 
                                       BridgeConfiguration.EndpointClient endpointClient,
                                       BridgeConfiguration.RestPath restPath) throws Exception {
        
        String routeId = "operation-" + serviceName + "-" + restPath.getOperation();
        
        log.info("Creando ruta: {}", routeId);

        from("direct:" + routeId)
            .routeId(routeId)
            .log("Ejecutando operación: " + restPath.getOperation())
            .setHeader("Content-Type", constant("application/json"))
            .setBody(constant("{\"message\": \"Gateway funcionando\", \"operation\": \"" + restPath.getOperation() + "\"}"))
            .process(jsonToXmlProcessor)
            .log("Respuesta procesada");
    }
}

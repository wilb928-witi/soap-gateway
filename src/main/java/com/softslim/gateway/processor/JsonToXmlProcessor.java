package com.softslim.gateway.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JsonToXmlProcessor implements Processor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void process(Exchange exchange) throws Exception {
        String jsonResponse = exchange.getIn().getBody(String.class);
        
        log.debug("Convirtiendo JSON a XML");
        
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonResponse);
            String xmlResponse = convertJsonToXml(jsonNode);
            
            exchange.getIn().setBody(xmlResponse);
            exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "text/xml");
            
        } catch (Exception e) {
            log.error("Error convirtiendo JSON a XML", e);
            throw new RuntimeException("Error en conversión JSON a XML", e);
        }
    }

    private String convertJsonToXml(JsonNode jsonNode) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<response>");
        buildXmlFromJson(jsonNode, xml);
        xml.append("</response>");
        return xml.toString();
    }

    private void buildXmlFromJson(JsonNode node, StringBuilder xml) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                xml.append("<").append(key).append(">");
                buildXmlFromJson(value, xml);
                xml.append("</").append(key).append(">");
            });
        } else if (node.isArray()) {
            node.forEach(item -> {
                xml.append("<item>");
                buildXmlFromJson(item, xml);
                xml.append("</item>");
            });
        } else {
            xml.append(node.asText());
        }
    }
}

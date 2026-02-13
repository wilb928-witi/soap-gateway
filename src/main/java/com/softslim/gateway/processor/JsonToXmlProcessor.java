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
            String xmlResponse = convertJsonToXmlFragment(jsonNode);
            
            exchange.getIn().setBody(xmlResponse);
            exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "text/xml");
            
        } catch (Exception e) {
            log.error("Error convirtiendo JSON a XML", e);
            throw new RuntimeException("Error en conversión JSON a XML", e);
        }
    }

    private String convertJsonToXmlFragment(JsonNode jsonNode) {
        StringBuilder xml = new StringBuilder();
        xml.append("<result>");
        buildXmlFromJson(jsonNode, xml);
        xml.append("</result>");
        return xml.toString();
    }

    private void buildXmlFromJson(JsonNode node, StringBuilder xml) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                String xmlTag = sanitizeXmlElementName(key);
                xml.append("<").append(xmlTag).append(">");
                buildXmlFromJson(value, xml);
                xml.append("</").append(xmlTag).append(">");
            });
        } else if (node.isArray()) {
            node.forEach(item -> {
                xml.append("<item>");
                buildXmlFromJson(item, xml);
                xml.append("</item>");
            });
        } else {
            xml.append(escapeXml(node.asText()));
        }
    }

    private String escapeXml(String value) {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }

    private String sanitizeXmlElementName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return "field";
        }

        String sanitized = rawName.replaceAll("[^A-Za-z0-9_\\-\\.]", "_");
        char first = sanitized.charAt(0);
        if (!Character.isLetter(first) && first != '_') {
            sanitized = "_" + sanitized;
        }
        return sanitized;
    }
}

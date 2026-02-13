package com.softslim.gateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class ApiDataFormatter {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FormattedData format(String rawData, String contentType) {
        if (rawData == null) {
            return new FormattedData("", false, false);
        }

        if (contentType != null && contentType.toLowerCase().contains("json")) {
            try {
                JsonNode jsonNode = objectMapper.readTree(rawData);
                StringBuilder xml = new StringBuilder();
                xml.append("<json>");
                buildXmlFromJson(jsonNode, xml);
                xml.append("</json>");
                return new FormattedData(xml.toString(), true, true);
            } catch (Exception ignored) {
                return new FormattedData(escapeXml(rawData), false, false);
            }
        }

        return new FormattedData(escapeXml(rawData), false, false);
    }

    public String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }

    private void buildXmlFromJson(JsonNode node, StringBuilder xml) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String tag = sanitizeXmlElementName(entry.getKey());
                xml.append("<").append(tag).append(">");
                buildXmlFromJson(entry.getValue(), xml);
                xml.append("</").append(tag).append(">");
            });
            return;
        }

        if (node.isArray()) {
            node.forEach(item -> {
                xml.append("<item>");
                buildXmlFromJson(item, xml);
                xml.append("</item>");
            });
            return;
        }

        xml.append(escapeXml(node.asText("")));
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

    public record FormattedData(String payload, boolean dataRedeable, boolean xmlPayload) {
    }
}

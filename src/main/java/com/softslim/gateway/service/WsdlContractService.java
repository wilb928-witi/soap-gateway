package com.softslim.gateway.service;

import com.softslim.gateway.model.BridgeConfiguration;
import org.springframework.stereotype.Service;

@Service
public class WsdlContractService {
    public String buildWsdl(
        String serviceName,
        BridgeConfiguration.EndpointClient endpointClient,
        String serviceUrl
    ) {
        String targetNamespace = "http://softslim.com/gateway/" + toXmlSafeName(serviceName);
        String wsdlServiceName = capitalize(toXmlSafeName(serviceName)) + "Service";
        String portTypeName = capitalize(toXmlSafeName(serviceName)) + "PortType";
        String bindingName = capitalize(toXmlSafeName(serviceName)) + "Binding";

        StringBuilder wsdl = new StringBuilder();
        wsdl.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        wsdl.append("<wsdl:definitions ");
        wsdl.append("xmlns:wsdl=\"http://schemas.xmlsoap.org/wsdl/\" ");
        wsdl.append("xmlns:soap=\"http://schemas.xmlsoap.org/wsdl/soap/\" ");
        wsdl.append("xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" ");
        wsdl.append("xmlns:tns=\"").append(escapeXml(targetNamespace)).append("\" ");
        wsdl.append("targetNamespace=\"").append(escapeXml(targetNamespace)).append("\">");

        wsdl.append("<wsdl:types>");
        wsdl.append("<xsd:schema targetNamespace=\"").append(escapeXml(targetNamespace)).append("\">");
        appendSchemaTypes(wsdl, endpointClient);
        wsdl.append("</xsd:schema>");
        wsdl.append("</wsdl:types>");

        appendMessages(wsdl, endpointClient);
        appendPortType(wsdl, endpointClient, portTypeName);
        appendBinding(wsdl, endpointClient, bindingName, portTypeName);
        appendService(wsdl, wsdlServiceName, bindingName, serviceUrl);

        wsdl.append("</wsdl:definitions>");
        return wsdl.toString();
    }

    private void appendSchemaTypes(StringBuilder wsdl, BridgeConfiguration.EndpointClient endpointClient) {
        if (endpointClient.getRest() == null || endpointClient.getRest().getPaths() == null) {
            return;
        }

        endpointClient.getRest().getPaths().forEach(restPath -> {
            String operation = toXmlSafeName(restPath.getOperation());

            wsdl.append("<xsd:element name=\"").append(operation).append("\">");
            wsdl.append("<xsd:complexType><xsd:sequence>");
            wsdl.append("<xsd:any minOccurs=\"0\" maxOccurs=\"unbounded\" processContents=\"lax\"/>");
            wsdl.append("</xsd:sequence></xsd:complexType>");
            wsdl.append("</xsd:element>");

            wsdl.append("<xsd:element name=\"").append(operation).append("Response\">");
            wsdl.append("<xsd:complexType><xsd:sequence>");
            wsdl.append("<xsd:any minOccurs=\"0\" maxOccurs=\"unbounded\" processContents=\"lax\"/>");
            wsdl.append("</xsd:sequence></xsd:complexType>");
            wsdl.append("</xsd:element>");
        });
    }

    private void appendMessages(StringBuilder wsdl, BridgeConfiguration.EndpointClient endpointClient) {
        if (endpointClient.getRest() == null || endpointClient.getRest().getPaths() == null) {
            return;
        }

        endpointClient.getRest().getPaths().forEach(restPath -> {
            String operation = toXmlSafeName(restPath.getOperation());
            wsdl.append("<wsdl:message name=\"").append(operation).append("Request\">");
            wsdl.append("<wsdl:part name=\"parameters\" element=\"tns:").append(operation).append("\"/>");
            wsdl.append("</wsdl:message>");

            wsdl.append("<wsdl:message name=\"").append(operation).append("Response\">");
            wsdl.append("<wsdl:part name=\"parameters\" element=\"tns:").append(operation).append("Response\"/>");
            wsdl.append("</wsdl:message>");
        });
    }

    private void appendPortType(
        StringBuilder wsdl,
        BridgeConfiguration.EndpointClient endpointClient,
        String portTypeName
    ) {
        wsdl.append("<wsdl:portType name=\"").append(escapeXml(portTypeName)).append("\">");
        if (endpointClient.getRest() != null && endpointClient.getRest().getPaths() != null) {
            endpointClient.getRest().getPaths().forEach(restPath -> {
                String operation = toXmlSafeName(restPath.getOperation());
                wsdl.append("<wsdl:operation name=\"").append(operation).append("\">");
                wsdl.append("<wsdl:input message=\"tns:").append(operation).append("Request\"/>");
                wsdl.append("<wsdl:output message=\"tns:").append(operation).append("Response\"/>");
                wsdl.append("</wsdl:operation>");
            });
        }
        wsdl.append("</wsdl:portType>");
    }

    private void appendBinding(
        StringBuilder wsdl,
        BridgeConfiguration.EndpointClient endpointClient,
        String bindingName,
        String portTypeName
    ) {
        wsdl.append("<wsdl:binding name=\"").append(escapeXml(bindingName)).append("\" type=\"tns:")
            .append(escapeXml(portTypeName))
            .append("\">");
        wsdl.append("<soap:binding style=\"document\" transport=\"http://schemas.xmlsoap.org/soap/http\"/>");

        if (endpointClient.getRest() != null && endpointClient.getRest().getPaths() != null) {
            endpointClient.getRest().getPaths().forEach(restPath -> {
                String operation = toXmlSafeName(restPath.getOperation());
                wsdl.append("<wsdl:operation name=\"").append(operation).append("\">");
                wsdl.append("<soap:operation soapAction=\"").append(operation).append("\"/>");
                wsdl.append("<wsdl:input><soap:body use=\"literal\"/></wsdl:input>");
                wsdl.append("<wsdl:output><soap:body use=\"literal\"/></wsdl:output>");
                wsdl.append("</wsdl:operation>");
            });
        }

        wsdl.append("</wsdl:binding>");
    }

    private void appendService(StringBuilder wsdl, String serviceName, String bindingName, String serviceUrl) {
        wsdl.append("<wsdl:service name=\"").append(escapeXml(serviceName)).append("\">");
        wsdl.append("<wsdl:port name=\"").append(escapeXml(serviceName)).append("Port\" binding=\"tns:")
            .append(escapeXml(bindingName))
            .append("\">");
        wsdl.append("<soap:address location=\"").append(escapeXml(serviceUrl)).append("\"/>");
        wsdl.append("</wsdl:port>");
        wsdl.append("</wsdl:service>");
    }

    private String toXmlSafeName(String value) {
        if (value == null || value.isBlank()) {
            return "operation";
        }
        String normalized = value.replaceAll("[^A-Za-z0-9_\\-\\.]", "_");
        if (!Character.isLetter(normalized.charAt(0)) && normalized.charAt(0) != '_') {
            return "_" + normalized;
        }
        return normalized;
    }

    private String capitalize(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }
        return Character.toUpperCase(input.charAt(0)) + input.substring(1);
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

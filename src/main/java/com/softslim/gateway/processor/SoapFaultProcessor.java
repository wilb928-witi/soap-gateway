package com.softslim.gateway.processor;

import com.softslim.gateway.exception.ApiInvocationException;
import com.softslim.gateway.service.ApiDataFormatter;
import com.softslim.gateway.service.GlobalExceptionHandlerService;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SoapFaultProcessor implements Processor {
    private final GlobalExceptionHandlerService globalExceptionHandlerService;
    private final ApiDataFormatter apiDataFormatter;

    public SoapFaultProcessor(
        GlobalExceptionHandlerService globalExceptionHandlerService,
        ApiDataFormatter apiDataFormatter
    ) {
        this.globalExceptionHandlerService = globalExceptionHandlerService;
        this.apiDataFormatter = apiDataFormatter;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Exception cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        globalExceptionHandlerService.handle(cause != null ? cause : new RuntimeException("Error desconocido"), exchange);

        int statusCode = 0;
        String data = cause != null ? cause.getMessage() : "Error desconocido";
        String apiContentType = "text/plain";
        if (cause instanceof ApiInvocationException apiInvocationException) {
            statusCode = apiInvocationException.getStatusCode();
            data = apiInvocationException.getResponseBody();
            apiContentType = apiInvocationException.getResponseContentType();
        }

        String operationName = exchange.getIn().getHeader("SoapOperation", String.class);
        if (operationName == null || operationName.isBlank()) {
            operationName = "gatewayError";
        }
        String namespace = exchange.getIn().getHeader("SoapNamespace", String.class);
        if (namespace == null || namespace.isBlank()) {
            namespace = "http://softslim.com/gateway";
        }

        ApiDataFormatter.FormattedData formattedData = apiDataFormatter.format(data, apiContentType);
        String soapResponse = buildSoapGatewayResponse(
            operationName,
            namespace,
            false,
            statusCode,
            formattedData.payload(),
            formattedData.dataRedeable(),
            formattedData.xmlPayload()
        );

        exchange.getIn().setBody(soapResponse);
        exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "text/xml");
        exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, statusCode > 0 ? statusCode : 500);
    }

    private String buildSoapGatewayResponse(
        String operationName,
        String namespace,
        boolean flag,
        int statusCode,
        String data,
        boolean dataRedeable,
        boolean xmlPayload
    ) {
        String dataNode = xmlPayload ? data : escapeXml(data);
        return
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
            "  <soap:Body>" +
            "    <ns:" + escapeXml(operationName) + "Response xmlns:ns=\"" + escapeXml(namespace) + "\">" +
            "      <success>" + flag + "</success>" +
            "      <statusCode>" + statusCode + "</statusCode>" +
            "      <dataRedeable>" + dataRedeable + "</dataRedeable>" +
            "      <data>" + dataNode + "</data>" +
            "    </ns:" + escapeXml(operationName) + "Response>" +
            "  </soap:Body>" +
            "</soap:Envelope>";
    }

    private String escapeXml(String value) {
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
}

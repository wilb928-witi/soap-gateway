package com.softslim.gateway.processor;

import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SoapFaultProcessor implements Processor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Exception cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        
        log.error("Generando SOAP Fault para excepción", cause);
        
        String faultCode = "Server";
        String faultString = cause != null ? cause.getMessage() : "Error desconocido";
        
        String soapFault = buildSoapFault(faultCode, faultString);
        
        exchange.getIn().setBody(soapFault);
        exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "text/xml");
        exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 500);
    }

    private String buildSoapFault(String faultCode, String faultString) {
        return String.format(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
            "  <soap:Body>" +
            "    <soap:Fault>" +
            "      <faultcode>soap:%s</faultcode>" +
            "      <faultstring>%s</faultstring>" +
            "    </soap:Fault>" +
            "  </soap:Body>" +
            "</soap:Envelope>",
            faultCode,
            faultString
        );
    }
}

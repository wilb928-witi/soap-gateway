package com.softslim.gateway.config;

import com.softslim.gateway.service.GlobalExceptionHandlerService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {
    private final GlobalExceptionHandlerService globalExceptionHandlerService;

    public GlobalExceptionHandler(GlobalExceptionHandlerService globalExceptionHandlerService) {
        this.globalExceptionHandlerService = globalExceptionHandlerService;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception exception, HttpServletRequest request) {
        String correlationId = request.getHeader("CorrelationId");
        String resolved = globalExceptionHandlerService.handle(exception, correlationId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body("Internal error. correlationId=" + resolved);
    }
}

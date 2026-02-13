package com.softslim.gateway.exception;

public class ApiInvocationException extends RuntimeException {
    private final int statusCode;
    private final String responseBody;
    private final String responseContentType;

    public ApiInvocationException(int statusCode, String responseBody, String responseContentType, Throwable cause) {
        super("Error invocando backend REST (status=" + statusCode + ")", cause);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
        this.responseContentType = responseContentType;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public String getResponseContentType() {
        return responseContentType;
    }

    public static ApiInvocationException internal(String message, Throwable cause) {
        return new ApiInvocationException(0, message, "text/plain", cause);
    }
}

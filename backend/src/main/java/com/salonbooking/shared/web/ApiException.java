package com.salonbooking.shared.web;

import org.springframework.http.HttpStatus;

/** An error the API reports to the client with a status, a stable code and a readable message. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}

package com.igirepay.pay_once_gateway.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class InProgressException extends RuntimeException {
    public InProgressException(String message) {
        super(message);
    }
}

package com.igirepay.pay_once_gateway.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class PayloadMismatchException extends RuntimeException {
    public PayloadMismatchException(String message) {
        super(message);
    }
}

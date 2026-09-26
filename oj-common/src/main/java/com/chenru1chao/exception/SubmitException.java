package com.chenru1chao.exception;

public class SubmitException extends RuntimeException{
    public SubmitException(String message, Throwable cause) {
        super(message, cause);
    }

    public SubmitException(String message) {
        super(message);
    }
}

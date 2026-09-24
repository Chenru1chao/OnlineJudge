package com.chenru1chao.exception;

public class ProblemNotFoundException extends RuntimeException {
    public ProblemNotFoundException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public ProblemNotFoundException(String msg) {
        super(msg);
    }
}

package com.chenru1chao.exception;

public class TestCaseNotFoundException extends RuntimeException {
    public TestCaseNotFoundException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public TestCaseNotFoundException(String msg) {
        super(msg);
    }
}

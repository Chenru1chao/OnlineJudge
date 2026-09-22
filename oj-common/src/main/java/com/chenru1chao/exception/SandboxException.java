package com.chenru1chao.exception;

public class SandboxException extends RuntimeException {
    public SandboxException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public SandboxException(String msg) {
        super(msg);
    }
}

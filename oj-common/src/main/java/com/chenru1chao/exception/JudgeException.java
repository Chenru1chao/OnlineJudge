package com.chenru1chao.exception;

public class JudgeException extends RuntimeException {
    public JudgeException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public JudgeException(String msg) {
        super(msg);
    }
}

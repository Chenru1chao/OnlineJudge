package com.chenru1chao.exception;

public class UserProfileNotFoundException extends RuntimeException {
    public UserProfileNotFoundException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public UserProfileNotFoundException(String msg) {
        super(msg);
    }
}

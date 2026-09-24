package com.chenru1chao.exception;

public class AliyunOssException extends RuntimeException {
    public AliyunOssException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public AliyunOssException(String msg) {
        super(msg);
    }
}

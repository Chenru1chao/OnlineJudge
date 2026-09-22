package com.chenru1chao.exception;

import com.chenru1chao.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandle {

    // TODO: 待完善全局异常处理器
    @ExceptionHandler(SandboxException.class)
    public Result<Void> handleExecException(SandboxException e) {
        log.error("测评机发生异常", e);
        return Result.error("系统繁忙，请稍后重试");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("程序抛出了异常", e);
        return Result.error("系统异常");
    }
}

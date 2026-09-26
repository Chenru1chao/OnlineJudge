package com.chenru1chao.exception;

import com.chenru1chao.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandle {

    // TODO: 待完善全局异常处理器
    @ExceptionHandler(SandboxException.class)
    public Result<Void> handleExecException(SandboxException sandboxException) {
        log.error("测评机发生异常", sandboxException);
        return Result.error("系统繁忙，请稍后重试");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("程序抛出了异常", e);
        return Result.error("系统异常");
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKeyException(DuplicateKeyException duplicateKeyException) {
        log.error("用户注册账号失败", duplicateKeyException);
        return Result.error("用户名或者是邮箱已存在!");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException methodArgumentNotValidException) {
        FieldError fe = methodArgumentNotValidException.getBindingResult().getFieldError();
        return Result.error(fe != null ? fe.getDefaultMessage() : "参数校验失败");
    }

    @ExceptionHandler(AliyunOssException.class)
    public Result<Void> handleAliyunOssException(AliyunOssException aliyunOssException) {
        return Result.error(aliyunOssException.getMessage());
    }

    @ExceptionHandler(ProblemNotFoundException.class)
    public Result<Void> handleProblemNotFoundException(ProblemNotFoundException problemNotFoundException) {
        return Result.error(problemNotFoundException.getMessage());
    }

    @ExceptionHandler(ProblemDataException.class)
    public Result<Void> handleProblemDataException(ProblemDataException problemDataException) {
        log.error("题目数据有问题", problemDataException);
        return Result.error(problemDataException.getMessage());
    }

    /**
     * 文件超过 spring.servlet.multipart.max-file-size 时 Spring 在进 Controller 之前就抛这个。
     * 不接的话会掉进兜底 handler，前端只会看到"系统异常"
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.warn("上传文件超过大小上限", e);
        return Result.error("文件太大，请压缩后再上传");
    }

    @ExceptionHandler(SubmitException.class)
    public Result<Void> handleSubmitException(SubmitException submitException) {
        log.error(submitException.getMessage());
        return Result.error(submitException.getMessage());
    }

}

package com.chenru1chao.exception;

/**
 * 题目数据本身有问题（样例文件缺失、文件名写错…）
 * 和 ProblemNotFoundException 的区别：那个是"题目不存在"，这个是"题目在，但配套数据不对"
 */
public class ProblemDataException extends RuntimeException {
    public ProblemDataException(String msg) {
        super(msg);
    }

    public ProblemDataException(String msg, Throwable cause) {
        super(msg, cause);
    }
}

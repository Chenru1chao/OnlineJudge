package com.chenru1chao.enums;

public enum JudgeStatus {
    PENDING(0, "排队中"),                // WJ Waiting
    JUDGING(1, "正在判题"),
    ACCEPTED(2, "AC 通过"),
    PARTIALLY_CORRECT(3, "PC 部分正确"),
    WRONG_ANSWER(4, "WA 答案错误"),
    TIME_LIMIT_EXCEEDED(5, "TLE 运行超时"),
    MEMORY_LIMIT_EXCEEDED(6, "MLE 内存超限"),
    RUNTIME_ERROR(7, "RE 运行时错误"),
    COMPILE_ERROR(8, "CE 编译错误"),
    OUTPUT_LIMIT_EXCEEDED(9, "OLE 输出超限"),
    UNKNOWN_ERROR(10, "UKE 未知错误"),
    COMPILE_SUCCESS(11, "编译成功");

    private final int code;
    private final String desc;

    JudgeStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
    public static JudgeStatus getByCode(int code) {
        for (JudgeStatus status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("无效判题状态码：" + code);
    }
}

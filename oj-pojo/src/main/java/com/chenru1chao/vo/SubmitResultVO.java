package com.chenru1chao.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SubmitResultVO {
    private Integer submitId;          // 提交记录 id（这轮可以先传 null，落库那轮补上）
    private Integer judgeStatus;       // JudgeStatus.code —— 给程序判断
    private String judgeStatusDesc;    // "AC 通过" —— 给用户看
    private Integer timeUsed;          // ms，没跑起来时为 null
    private Integer failedCaseNo;      // 第几个用例失败，AC/CE 时为 null
    private String message;            // CE 的编译错误 / RE 的堆栈，其余为 null
}

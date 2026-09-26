package com.chenru1chao.judge.model;

import com.chenru1chao.enums.JudgeStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RunResult {
    private JudgeStatus judgeStatus;
    private String stdout;
    private String stderr;
    private Integer failedCaseNo;
    private Integer timeUsedMs;
    private Integer memoryUsedKb;
}

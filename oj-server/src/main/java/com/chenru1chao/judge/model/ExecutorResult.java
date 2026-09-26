package com.chenru1chao.judge.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExecutorResult {
    private Integer exitCode;
    private String stdout;
    private String stderr;
    private Boolean timedOut;
    private Integer timeUsedMs;
    private Boolean outputExceeded;
    private Integer memoryUsedKb;
}

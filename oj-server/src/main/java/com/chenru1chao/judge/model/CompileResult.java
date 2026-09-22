package com.chenru1chao.judge.model;

import com.chenru1chao.enums.JudgeStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.nio.file.Path;

@Data
@AllArgsConstructor
public class CompileResult {
    private JudgeStatus judgeStatus;
    private String stdout;
    private String stderr;
    private Path workDir;
}

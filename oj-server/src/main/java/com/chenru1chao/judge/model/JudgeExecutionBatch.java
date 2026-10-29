package com.chenru1chao.judge.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 一次沙箱执行的完整结果。
 * 编译和运行在同一个容器里完成，所以编译结果必须和用例结果一起回来。
 */
@Data
@AllArgsConstructor
public class JudgeExecutionBatch {
    /** 编译失败的 javac 报错；为 null 表示编译成功 */
    private String compileError;

    /** 每个用例的执行结果；编译失败时为空列表 */
    private List<JudgeCaseResult> results;
}

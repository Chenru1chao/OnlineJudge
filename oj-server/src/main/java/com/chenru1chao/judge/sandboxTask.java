package com.chenru1chao.judge;

import com.chenru1chao.dto.SubmitDTO;
import com.chenru1chao.entity.Problem;
import com.chenru1chao.entity.Submit;
import com.chenru1chao.enums.JudgeStatus;
import com.chenru1chao.judge.model.CompileResult;
import com.chenru1chao.judge.model.RunResult;
import com.chenru1chao.judge.model.TestCase;
import com.chenru1chao.service.impl.SubmitServiceImpl;
import lombok.AllArgsConstructor;

import java.util.List;

@AllArgsConstructor
public class sandboxTask implements Runnable {
    private Integer submitId;
    private SubmitDTO submitDTO;
    private Problem problem;
    private List<TestCase> testCases;

    private final SandboxCompiler sandboxCompiler;
    private final SandboxRunner sandboxRunner;
    private final SubmitServiceImpl submitService;

    @Override
    public void run() {
        try {
            Submit submit = Submit.builder().id(submitId).build();
            submit.setStatus(JudgeStatus.JUDGING.getCode());
            submitService.updateById(submit);

            CompileResult compileResult = sandboxCompiler.compile(submitDTO.getCode());

            if (compileResult.getJudgeStatus() != JudgeStatus.COMPILE_SUCCESS) {
                submit.setErrorMsg(compileResult.getStderr());
                submit.setStatus(compileResult.getJudgeStatus().getCode());
                submitService.updateById(submit);
                return;
            }

            RunResult runResult = sandboxRunner.run(compileResult.getWorkDir(), testCases,
                    problem.getTimeLimit(), problem.getMemoryLimit());

            submit.setStatus(runResult.getJudgeStatus().getCode());
            submit.setFailedCaseNo(runResult.getFailedCaseNo());
            submit.setErrorMsg(runResult.getStderr());
            submit.setTimeUsed(runResult.getTimeUsedMs());
            submit.setMemoryUsed(runResult.getMemoryUsedKb());

            submitService.updateById(submit);

        } catch (Exception e) {
            // 如果Sandbox抛出异常 这里是异步调用处理不了 让子线程自己处理
            Submit submit = Submit.builder().id(submitId).build();
            submit.setStatus(JudgeStatus.UNKNOWN_ERROR.getCode());
            submit.setErrorMsg(e.getMessage());
            submitService.updateById(submit);
        }
    }
}
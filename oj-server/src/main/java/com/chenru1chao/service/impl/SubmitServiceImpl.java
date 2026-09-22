package com.chenru1chao.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chenru1chao.dto.SubmitDTO;
import com.chenru1chao.entity.Problem;
import com.chenru1chao.entity.Submit;
import com.chenru1chao.enums.JudgeStatus;
import com.chenru1chao.exception.SandboxException;
import com.chenru1chao.judge.SandboxCompiler;
import com.chenru1chao.judge.SandboxRunner;
import com.chenru1chao.judge.SandboxTestCaseLoader;
import com.chenru1chao.judge.model.CompileResult;
import com.chenru1chao.judge.model.RunResult;
import com.chenru1chao.judge.model.TestCase;
import com.chenru1chao.mapper.SubmitMapper;
import com.chenru1chao.result.Result;
import com.chenru1chao.service.IProblemService;
import com.chenru1chao.service.ISubmitService;
import com.chenru1chao.vo.SubmitResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SubmitServiceImpl extends ServiceImpl<SubmitMapper, Submit> implements ISubmitService {

    private final SandboxCompiler sandboxCompiler;
    private final SandboxRunner sandboxRunner;
    private final SandboxTestCaseLoader sandboxTestCaseLoader;
    private final IProblemService iProblemService;
    @Override
    public Result<SubmitResultVO> handleUserSubmit(SubmitDTO submitDTO)  {
        CompileResult compileResult = sandboxCompiler.compile(submitDTO.getCode());

        if (compileResult.getJudgeStatus() != JudgeStatus.COMPILE_SUCCESS) {

            Submit submit = new Submit(
                    null,
                    submitDTO.getUserId(),
                    submitDTO.getProblemId(),
                    compileResult.getJudgeStatus().getCode(),
                    null,
                    compileResult.getStderr(),
                    "java",
                    null,
                    null,
                    submitDTO.getCode(),
                    null);

            save(submit);

            return Result.success(new SubmitResultVO(
                    submit.getId(),
                    compileResult.getJudgeStatus().getCode(),
                    compileResult.getJudgeStatus().getDesc(),
                    null,
                    null,
                    compileResult.getStderr()));
        }

        // TODO: 这个地方后面用MQ改成异步调用
        Problem problem = iProblemService.lambdaQuery().eq(Problem::getId, submitDTO.getProblemId()).one();

        if (problem == null) {
            throw new SandboxException("题目不存在: " + submitDTO.getProblemId());
        }

        List<TestCase> testCases = sandboxTestCaseLoader.testCaseLoader(submitDTO.getProblemId());

        if (testCases.isEmpty()) {
            throw new SandboxException("题目测试用例不存在: " + submitDTO.getProblemId());
        }

        RunResult runResult = sandboxRunner.run(compileResult.getWorkDir(), testCases,
                problem.getTimeLimit(), problem.getMemoryLimit());

        SubmitResultVO submitResultVO = new SubmitResultVO(
                null,
                runResult.getJudgeStatus().getCode(),
                runResult.getJudgeStatus().getDesc(),
                runResult.getTimeUsedMs(),
                null,
                null);

        Submit submit = new Submit(
                null,
                submitDTO.getUserId(),
                submitDTO.getProblemId(),
                runResult.getJudgeStatus().getCode(),
                runResult.getFailedCaseNo(),
                runResult.getStderr(),
                "java",
                runResult.getTimeUsedMs(),
                null,
                submitDTO.getCode(),
                null);

        save(submit);

        submitResultVO.setSubmitId(submit.getId());

        if (runResult.getJudgeStatus() != JudgeStatus.ACCEPTED) {
            submitResultVO.setFailedCaseNo(runResult.getFailedCaseNo());
            submitResultVO.setMessage(runResult.getStderr());
        }

        return Result.success(submitResultVO);
    }
}

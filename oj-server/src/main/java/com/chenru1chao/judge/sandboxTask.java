package com.chenru1chao.judge;

import com.chenru1chao.dto.SubmitDTO;
import com.chenru1chao.entity.Problem;
import com.chenru1chao.entity.Submit;
import com.chenru1chao.entity.UserAccept;
import com.chenru1chao.enums.JudgeStatus;
import com.chenru1chao.judge.model.CompileResult;
import com.chenru1chao.judge.model.RunResult;
import com.chenru1chao.judge.model.TestCase;
import com.chenru1chao.service.IProblemService;
import com.chenru1chao.service.IUserAcceptService;
import com.chenru1chao.service.impl.SubmitServiceImpl;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

@AllArgsConstructor
@Slf4j
public class sandboxTask implements Runnable {
    private Integer userId;
    private Integer submitId;
    private SubmitDTO submitDTO;
    private Problem problem;
    private List<TestCase> testCases;

    private final SandboxCompiler sandboxCompiler;
    private final SandboxRunner sandboxRunner;
    private final SubmitServiceImpl submitService;
    private final IUserAcceptService iUserAcceptService;
    private final IProblemService iProblemService;

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

            if (runResult.getJudgeStatus() == JudgeStatus.ACCEPTED) {
                UserAccept userAccept = new UserAccept();
                userAccept.setUserId(userId);
                userAccept.setProblemId(problem.getId());
                // 更新题目的通过次数
                try {
                    iProblemService.lambdaUpdate().eq(Problem::getId, problem.getId())
                        .setSql("pass_total = pass_total + 1").update();
                } catch (Exception e) {
                    log.error("更新问题通过次数失败 问题编号:{}", problem.getId(), e);
                }
                try {
                    // 尝试保存用户通过题目记录
                    iUserAcceptService.save(userAccept);
                    // TODO: 后续做缓存时需在这里重构缓存!!!
                } catch (DuplicateKeyException e) {
                    // 撞unique(user_id, problem_id) 说明之前已经AC过 正是我们要的结果不是错误
                }
            }
        } catch (Exception e) {
            // 如果Sandbox抛出异常 这里是异步调用处理不了 让子线程自己处理
            Submit submit = Submit.builder().id(submitId).build();
            submit.setStatus(JudgeStatus.UNKNOWN_ERROR.getCode());
            submit.setErrorMsg(e.getMessage());
            submitService.updateById(submit);
            log.error("测评机出现异常 异常信息:{}", e.getMessage(), e);
        }
    }
}
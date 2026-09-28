package com.chenru1chao.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chenru1chao.dto.SubmitDTO;
import com.chenru1chao.entity.Problem;
import com.chenru1chao.entity.Submit;
import com.chenru1chao.enums.JudgeStatus;
import com.chenru1chao.event.SubmitEvent;
import com.chenru1chao.exception.SandboxException;
import com.chenru1chao.exception.SubmitException;
import com.chenru1chao.judge.SandboxCompiler;
import com.chenru1chao.judge.SandboxRunner;
import com.chenru1chao.judge.SandboxTestCaseLoader;
import com.chenru1chao.judge.model.TestCase;
import com.chenru1chao.judge.sandboxTask;
import com.chenru1chao.mapper.SubmitMapper;
import com.chenru1chao.result.Result;
import com.chenru1chao.service.IProblemService;
import com.chenru1chao.service.ISubmitService;
import com.chenru1chao.service.IUserAcceptService;
import com.chenru1chao.util.UserContext;
import com.chenru1chao.vo.SubmitVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ExecutorService;

@Service
@Slf4j
@RequiredArgsConstructor
public class SubmitServiceImpl extends ServiceImpl<SubmitMapper, Submit> implements ISubmitService {

    private final SandboxCompiler sandboxCompiler;
    private final SandboxRunner sandboxRunner;
    private final SandboxTestCaseLoader sandboxTestCaseLoader;
    private final IProblemService iProblemService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ExecutorService sandboxTaskExecutor;
    private final IUserAcceptService iUserAcceptService;


    // TODO: 后续如果拆分微服务了 使用RabbitMQ
    // 异步调用没法抛异常了 提前判定题目和测试用例存在 决定是否要抛出异常 再来跑编译和运行
    @Override
    public Result<SubmitVO> handleUserSubmit(SubmitDTO submitDTO)  {
        Problem problem = iProblemService.lambdaQuery().eq(Problem::getId, submitDTO.getProblemId()).one();

        // 提前判断 如果前端传来的数据异常 直接抛出异常
        if (problem == null)
            throw new SandboxException("题目不存在: " + submitDTO.getProblemId());

        List<TestCase> testCases = sandboxTestCaseLoader.testCaseLoader(submitDTO.getProblemId());

        if (testCases.isEmpty())
            throw new SandboxException("题目测试用例不存在: " + submitDTO.getProblemId());

        Submit submit = BeanUtil.copyProperties(submitDTO, Submit.class);
        submit.setUserId(UserContext.get());
        submit.setStatus(JudgeStatus.PENDING.getCode());

        save(submit);

        Integer submitId = submit.getId();

        try {
            // 异步判题 提交任务给线程池
            sandboxTaskExecutor.submit(new sandboxTask(
                    UserContext.get(), submitId, submitDTO, problem, testCases,
                    sandboxCompiler, sandboxRunner, this,
                    iUserAcceptService, iProblemService));
        } catch (Exception e) {
            submit.setStatus(JudgeStatus.UNKNOWN_ERROR.getCode());
            submit.setErrorMsg("判题队列已满 请稍后重新提交");
            updateById(submit);
        }

        try {
            applicationEventPublisher.publishEvent(new SubmitEvent(UserContext.get(), submitDTO.getProblemId()));
        } catch (Exception e) {
            log.error("发布更新用户提交事件失败 userId={}", UserContext.get());
        }

        SubmitVO submitVO = BeanUtil.copyProperties(submit, SubmitVO.class);

        return Result.success(submitVO);
    }
    @Override
    public Result<SubmitVO> getSubmitStatus(Integer id) {
        Submit submit = lambdaQuery().select(Submit::getId, Submit::getUserId, Submit::getProblemId,
                        Submit::getStatus, Submit::getTimeUsed, Submit::getMemoryUsed, Submit::getFailedCaseNo,
                        Submit::getErrorMsg, Submit::getSubmitLanguage, Submit::getSubmitTime)
                .eq(Submit::getId, id).eq(Submit::getUserId, UserContext.get()).one();

        if (submit == null) {
            throw new SubmitException("当前提交记录不存在");
        }

        SubmitVO submitVO = BeanUtil.copyProperties(submit, SubmitVO.class);
        return Result.success(submitVO);
    }
}

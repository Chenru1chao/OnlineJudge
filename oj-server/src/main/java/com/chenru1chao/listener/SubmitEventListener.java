package com.chenru1chao.listener;

import com.chenru1chao.entity.Problem;
import com.chenru1chao.entity.User;
import com.chenru1chao.event.SubmitEvent;
import com.chenru1chao.service.IProblemService;
import com.chenru1chao.service.IUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubmitEventListener {

    private final IUserService iUserService;
    private final IProblemService iProblemService;

    // 这个地方没加事务 有定时任务兜底 弱一致场景下能接受
    @Async("userSubmitEventExecutor")
    @EventListener(value = SubmitEvent.class)
    public void updateUserTotalSubmit(SubmitEvent submitEvent) {
        try {
            // 更新用户的提交记录
            iUserService.lambdaUpdate().eq(User::getId, submitEvent.getUserId())
                .setSql("total_submit = total_submit + 1").update();
        } catch (Exception e) {
            log.error("更新用户总提交失败 userId={}", submitEvent.getUserId(), e);
        }

        try {
            // 更新题目的提交记录
            iProblemService.lambdaUpdate().eq(Problem::getId, submitEvent.getProblemId())
                    .setSql("submit_total = submit_total + 1").update();
        } catch (Exception e) {
            log.error("更新题目总提交失败 problemId={}", submitEvent.getProblemId(), e);
        }
    }
}

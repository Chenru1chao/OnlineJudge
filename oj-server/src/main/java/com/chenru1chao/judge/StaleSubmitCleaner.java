package com.chenru1chao.judge;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chenru1chao.entity.Submit;
import com.chenru1chao.enums.JudgeStatus;
import com.chenru1chao.mapper.SubmitMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class StaleSubmitCleaner implements SmartInitializingSingleton {

    private final SubmitMapper submitMapper;

    private static final Integer STALE_MINUTES = 60;

    // 设置定时任务 清理异常提交记录
    @Scheduled(cron = "0 */2 * * * *")
    public void cleanStaleSubmissions() {
        LocalDateTime staleTime = LocalDateTime.now().minusMinutes(STALE_MINUTES);

        LambdaUpdateWrapper<Submit> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Submit::getStatus, JudgeStatus.JUDGING.getCode())
                .le(Submit::getSubmitTime, staleTime)
                .set(Submit::getErrorMsg, "判题超时中断 请重新提交")
                .set(Submit::getStatus, JudgeStatus.UNKNOWN_ERROR.getCode());

        int updateRow = submitMapper.update(wrapper);
        log.info("定时任务:处理了{}条异常提交", updateRow);
    }

    // 启动清理 刚刚启动时一定是没有排队中的提交
    @Override
    public void afterSingletonsInstantiated() {
        LambdaUpdateWrapper<Submit> wrapper = new LambdaUpdateWrapper<>();
        wrapper.in(Submit::getStatus, JudgeStatus.JUDGING.getCode(), JudgeStatus.PENDING.getCode())
                .set(Submit::getErrorMsg, "服务重启判题中断 请重新提交")
                .set(Submit::getStatus, JudgeStatus.UNKNOWN_ERROR.getCode());

        int updateRow = submitMapper.update(wrapper);
        log.info("启动清理:处理了{}条异常提交", updateRow);
    }
}

package com.chenru1chao.task;

import com.chenru1chao.mapper.SubmitMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserSubmitChecker {

    private final SubmitMapper submitMapper;

    // TODO: 活跃用户过多时需分批处理
    // TODO: 给Submit表添加索引 submit_time user字段
    @Scheduled(cron = "0 0 0 * * *")
    public void checkUserTotalSubmit() {
        try {
            LocalDateTime since = LocalDateTime.now().minusDays(2);
            List<Integer> userIds = submitMapper.selectActiveUserIds(since);

            if (userIds.isEmpty()) {
                log.info("用户提交数对账完成 近期无用户新增提交");
                return;
            }

            Integer totalFixed = submitMapper.reconcileTotalSubmit(userIds);
            log.info("活跃用户提交数对账完成 活跃用户{}个 修正{}行", userIds.size(), totalFixed);
        } catch (Exception e) {
            log.error("用户提交数对账失败", e);
        }
    }

    // TODO: 题目提交数量的兜底方案 可以采用和上面一样的方式 近期由提交的题目重新记数
}

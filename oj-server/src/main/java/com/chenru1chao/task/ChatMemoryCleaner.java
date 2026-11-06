package com.chenru1chao.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * 定期清理过期的 AI 会话记忆。
 *
 * <p>记住太多历史没有意义：一方面每次请求都会把最近的消息带进上下文，
 * 另一方面表会越积越大。这里只保留最近 N 天（默认 7 天），
 * 单用户的消息上限则由 TokenWindowChatMemory 按 token 预算控制。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMemoryCleaner {

    private final JdbcTemplate jdbcTemplate;

    @Value("${oj.ai.memory.retention-days:7}")
    private int retentionDays;

    @Scheduled(cron = "0 40 3 * * *")
    public void purgeExpired() {
        Timestamp before = Timestamp.valueOf(LocalDateTime.now().minusDays(retentionDays));
        int deleted = jdbcTemplate.update(
                "DELETE FROM SPRING_AI_CHAT_MEMORY WHERE `timestamp` < ?", before);
        if (deleted > 0) {
            log.info("清理过期 AI 会话记忆 {} 条（早于 {}）", deleted, before);
        }
    }
}

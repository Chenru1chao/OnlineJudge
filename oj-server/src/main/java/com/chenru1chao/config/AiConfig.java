package com.chenru1chao.config;

import com.chenru1chao.ai.TokenWindowChatMemory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 相关 Bean。
 *
 * <p>会话记忆用 MySQL 持久化（JdbcChatMemoryRepository 由 starter 自动装配），
 * 外面再包一层按 token 预算裁剪的 TokenWindowChatMemory，
 * 避免历史越长越拖慢/越贵。预算和条数都可以在 application.yml 里调。</p>
 */
@Configuration
public class AiConfig {

    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository repository,
                                 @Value("${oj.ai.memory.max-tokens:3000}") int maxTokens,
                                 @Value("${oj.ai.memory.max-messages:10}") int maxMessages) {
        return new TokenWindowChatMemory(repository, maxTokens, maxMessages);
    }
}

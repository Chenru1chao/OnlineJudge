package com.chenru1chao.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;

@Configuration
public class JudgeThreadPoolConfig {

    @Bean
    public ExecutorService judgeTaskExecutor() {
        // 异步调用 创建一个线程池 最大线程数为4 最大等待数50 拒绝策略 达到上线后直接拒绝任务
        return new ThreadPoolExecutor(
                4, 4, 1,
                TimeUnit.SECONDS, new ArrayBlockingQueue<>(200), new ThreadPoolExecutor.AbortPolicy());
    }
}

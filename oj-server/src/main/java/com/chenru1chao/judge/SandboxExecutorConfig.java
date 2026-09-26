package com.chenru1chao.judge;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;

@Configuration
public class SandboxExecutorConfig {
    @Bean
    public ExecutorService sandboxTaskExecutor() {
        // 异步调用创建一个线程池 最大线程数为4 最大等待数50 拒绝策略 抛出异常
        return new ThreadPoolExecutor(
                4, 4, 1,
                TimeUnit.SECONDS, new ArrayBlockingQueue<>(50), new ThreadPoolExecutor.AbortPolicy());
    }
}

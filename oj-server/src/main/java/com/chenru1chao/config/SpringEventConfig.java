package com.chenru1chao.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class SpringEventConfig {

    @Bean("userSubmitEventExecutor")
    public ThreadPoolExecutor userSubmitEventExecutor() {
        return new ThreadPoolExecutor(
                2, 2, 50,
                TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(100),
                Executors.defaultThreadFactory(), new ThreadPoolExecutor.AbortPolicy());
    }
}

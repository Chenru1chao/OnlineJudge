package com.chenru1chao.config;

import com.chenru1chao.interceptor.LoginInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@RequiredArgsConstructor
public class SpringMvcConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                    "/user/login", 
                    "/user/register", 
                    "/user/logout",
                    "/problem/**",
                    "/ai/ping",
                    "/ai-test.html",
                    "/tag/list");
    }

    /**
     * 给异步请求（如 SSE 流式推荐）配一个可复用的线程池，
     * 替换 Spring MVC 默认的 SimpleAsyncTaskExecutor（来一个请求 new 一个线程，高并发下会崩）。
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("mvc-async-");
        // 队列满时由调用线程执行，避免直接丢任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        configurer.setTaskExecutor(executor);
    }
}

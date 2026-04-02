package com.example.socialnetwork.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@EnableAsync
@Configuration
public class AsyncConfig {

    @Bean("fraudCheckExecutor")
    public TaskExecutor fraudCheckExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("fraud-check-");
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.DiscardPolicy()
        );
        executor.initialize();
        return executor;
    }
}

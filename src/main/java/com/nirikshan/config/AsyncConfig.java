package com.nirikshan.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.core.task.SyncTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {
    @Bean(name = "riskEventTaskExecutor")
    @Profile("prod")
    public Executor riskEventTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("risk-analysis-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "riskEventTaskExecutor")
    @Profile("!prod")
    public Executor synchronousRiskEventTaskExecutor() {
        return new SyncTaskExecutor();
    }
}

package com.game.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("highPriorityIngestExecutor")
    public Executor highPriorityIngestExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2, 4,
                60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                r -> {
                    Thread t = new Thread(r, "high-priority-ingest");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    @Bean("defaultIngestExecutor")
    public Executor defaultIngestExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 2,
                60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(50),
                r -> {
                    Thread t = new Thread(r, "default-ingest");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
}

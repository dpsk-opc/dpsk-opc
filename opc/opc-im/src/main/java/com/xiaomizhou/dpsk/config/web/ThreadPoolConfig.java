package com.xiaomizhou.dpsk.config.web;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/6/1 13:19
 * @description
 */
@Configuration
@Slf4j
public class ThreadPoolConfig {

    @Value("${thread.pool.core.pool.size:10}")
    private Integer corePoolSize = 10;

    @Value("${thread.pool.max.pool.size:20}")
    private Integer maximumPoolSize = 20;

    @Value("${thread.pool.keep.alive.time:60}")
    private Long keepAliveTime = 60L;

    @Value("${thread.pool.work.queue.capacity:1000}")
    private Integer workQueueCapacity = 1000;

    /**
     * 线程池配置
     *
     * @return
     */
    @Bean
    public ExecutorService threadPoolExecutor() {

        ThreadPoolExecutor executor = new ThreadPoolExecutor(corePoolSize,
                maximumPoolSize,
                keepAliveTime,
                TimeUnit.SECONDS, new LinkedBlockingQueue<>(workQueueCapacity));

        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setThreadFactory(BasicThreadFactory.builder()
                .uncaughtExceptionHandler((t, e) -> {
                    log.error("ThreadPoolExecutor uncaughtExceptionHandler: {}", t.getName(), e);
                })
                .namingPattern("thread-pool-%d")
                .build());
        return executor;
    }

}

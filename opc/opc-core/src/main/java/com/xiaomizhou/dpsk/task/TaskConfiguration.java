package com.xiaomizhou.dpsk.task;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.xiaomizhou.dpsk.task.consumer.TaskConsumerRegistry;
import com.xiaomizhou.dpsk.task.repository.TaskExecutionLogRepository;
import com.xiaomizhou.dpsk.task.repository.TaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import javax.sql.DataSource;
import java.util.concurrent.ExecutorService;

/**
 * 定时任务系统自动配置。
 * <p>
 * 启动时自动：
 * 1. 创建 TaskConsumerRegistry、TaskManager 等核心组件
 * 2. 加载存量定时任务到调度器
 *
 * @author eason - vipzhsh@163.com
 */
@Configuration
@Slf4j
@Order
public class TaskConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TaskConsumerRegistry taskConsumerRegistry() {
        return new TaskConsumerRegistry();
    }

    @Bean(destroyMethod = "stop")
    @ConditionalOnMissingBean
    public TaskManager taskManager(TaskRepository taskRepository,
                                   TaskExecutionLogRepository executionLogRepository,
                                   TaskConsumerRegistry consumerRegistry,
                                   ExecutorService executor,
                                   DataSource dataSource) {
        TaskManagerImpl taskManager = new TaskManagerImpl(
                taskRepository, executionLogRepository, consumerRegistry, executor, dataSource, JsonMapper.builder()
                // 忽略未知字段，避免反序列化时报错
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                // 允许字段名不带引号（非标准，慎用，通常关闭）
                // .configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, false)
                // 日期格式：ISO-8601 或自定义
                // .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                // 序列化时排除 null 值
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                // 支持 Java 8 时间类型
                .addModule(new JavaTimeModule())
                .build());

        // 启动调度器并加载存量定时任务
        try {
            taskManager.start();
        } catch (Exception e) {
            log.warn("Failed to start TaskManager on startup (may be normal if DB not ready)", e);
        }

        return taskManager;
    }
}

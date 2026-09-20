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
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
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

        // 注意：这里【不能】直接调用 taskManager.start()。
        // 原因：@Bean 方法在容器刷新早期执行，此时 Flyway 尚未完成迁移，
        // 而 db-scheduler 依赖 scheduled_tasks 表（由 V1__init.sql 创建）。
        // 提前启动会导致后台轮询线程持续报 "Table SCHEDULED_TASKS not found"，
        // 且该线程不会在表建好后自动恢复。
        // 因此启动推迟到 ApplicationReadyEvent（见 TaskManagerStartupInitializer）。
        return taskManager;
    }

    /**
     * 任务调度启动器：在应用就绪后启动 db-scheduler。
     * <p>
     * 执行时机晚于 Flyway 数据库迁移（Flyway 在容器刷新阶段完成，本事件在其之后触发），
     * 保证 {@code scheduled_tasks} 表已存在。
     * <p>
     * order 设为 0，与工具注册初始化器一致（两者互相独立，但都需早于业务逻辑）。
     */
    @Component
    @Slf4j
    @Order(0)
    public static class TaskManagerStartupInitializer implements ApplicationListener<ApplicationReadyEvent> {

        /**
         * db-scheduler 内部表名（由 V1__init.sql 创建）
         */
        private static final String SCHEDULED_TASKS_TABLE = "scheduled_tasks";

        private final TaskManager taskManager;

        private final DataSource dataSource;

        public TaskManagerStartupInitializer(TaskManager taskManager, DataSource dataSource) {
            this.taskManager = taskManager;
            this.dataSource = dataSource;
        }

        @Override
        public void onApplicationEvent(ApplicationReadyEvent event) {
            // 前置校验：db-scheduler 依赖 scheduled_tasks 表。
            // 若表不存在就启动，后台轮询线程会持续报错且无法自动恢复，
            // 因此这里提前判断并给出明确日志。
            if (!tableExists(SCHEDULED_TASKS_TABLE)) {
                log.error("Table '{}' not found, task scheduler will NOT start. "
                        + "Please check that Flyway migration V1__init.sql has been applied successfully.",
                        SCHEDULED_TASKS_TABLE);
                return;
            }
            try {
                taskManager.start();
                log.info("Task scheduler started after application ready");
            } catch (Exception e) {
                log.error("Failed to start TaskManager after application ready", e);
            }
        }

        /**
         * 判断表是否存在（兼容大小写，H2/MySQL 通用）。
         */
        private boolean tableExists(String tableName) {
            try (Connection connection = dataSource.getConnection()) {
                DatabaseMetaData metaData = connection.getMetaData();
                try (ResultSet rs = metaData.getTables(connection.getCatalog(), null, null,
                        new String[]{"TABLE"})) {
                    while (rs.next()) {
                        if (tableName.equalsIgnoreCase(rs.getString("TABLE_NAME"))) {
                            return true;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to check table existence: {}", tableName, e);
                // 校验失败时保持宽容，交由 taskManager.start() 自行报错
                return true;
            }
            return false;
        }
    }
}

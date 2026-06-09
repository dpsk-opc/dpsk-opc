package com.xiaomizhou.dpsk.task.scheduler;

import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.task.TaskInstanceId;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.time.Duration;

/**
 * db-scheduler 封装，仅处理定时任务。
 * <p>
 * 每个业务定时任务在 db-scheduler 中对应一个 RecurringTask，
 * taskName 格式为 "task_" + taskCode。
 *
 * @author eason - vipzhsh@163.com
 */
@Slf4j
public class DbSchedulerWrapper {

    private static final String TASK_PREFIX = "task_";

    private final Scheduler scheduler;

    public DbSchedulerWrapper(DataSource dataSource) {
        this.scheduler = Scheduler.create(dataSource)
                .threads(1)
                .pollingInterval(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 启动调度器。
     */
    public void start() {
        scheduler.start();
        log.info("DbScheduler started");
    }

    /**
     * 停止调度器。
     */
    public void stop() {
        scheduler.stop();
        log.info("DbScheduler stopped");
    }

    /**
     * 获取底层的 Scheduler 实例，供 TaskManagerImpl 使用。
     */
    public Scheduler getScheduler() {
        return scheduler;
    }

    /**
     * 获取调度器中的任务名称，格式为 "task_" + taskCode。
     */
    public static String toTaskName(String taskCode) {
        return TASK_PREFIX + taskCode;
    }

    /**
     * 检查任务是否已被调度。
     */
    public boolean isScheduled(String taskCode) {
        return scheduler.getScheduledExecution(
                TaskInstanceId.of(toTaskName(taskCode), taskCode)
        ).isPresent();
    }
}

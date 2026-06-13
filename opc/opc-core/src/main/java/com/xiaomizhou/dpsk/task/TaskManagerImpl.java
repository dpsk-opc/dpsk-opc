package com.xiaomizhou.dpsk.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.jdbc.JdbcCustomization;
import com.github.kagkarlsson.scheduler.serializer.JacksonSerializer;
import com.github.kagkarlsson.scheduler.task.*;
import com.github.kagkarlsson.scheduler.task.helper.RecurringTask;
import com.github.kagkarlsson.scheduler.task.helper.RecurringTaskWithPersistentSchedule;
import com.github.kagkarlsson.scheduler.task.helper.ScheduleAndData;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import com.github.kagkarlsson.scheduler.task.schedule.CronSchedule;
import com.github.kagkarlsson.scheduler.task.schedule.FixedDelay;
import com.github.kagkarlsson.scheduler.task.schedule.Schedule;
import com.github.kagkarlsson.scheduler.task.schedule.Schedules;
import com.google.common.collect.Lists;
import com.xiaomizhou.dpsk.task.consumer.TaskConsumer;
import com.xiaomizhou.dpsk.task.consumer.TaskConsumerRegistry;
import com.xiaomizhou.dpsk.task.model.Task;
import com.xiaomizhou.dpsk.task.model.TaskConsumeResult;
import com.xiaomizhou.dpsk.task.model.TaskExecutionLog;
import com.xiaomizhou.dpsk.task.repository.TaskExecutionLogRepository;
import com.xiaomizhou.dpsk.task.repository.TaskRepository;
import com.xiaomizhou.dpsk.task.scheduler.DbSchedulerWrapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.lang.Thread.sleep;

/**
 * 任务管理器实现。
 *
 * @author eason - vipzhsh@163.com
 */
@Slf4j
public class TaskManagerImpl implements TaskManager {

    private final TaskRepository taskRepository;

    private final TaskExecutionLogRepository executionLogRepository;
    private final TaskConsumerRegistry consumerRegistry;
    private  Scheduler scheduler;
    private final ExecutorService executor;
    private final ObjectMapper objectMapper;

    private final TaskDescriptor<ScheduleAndNoData> DESCRIPTOR = TaskDescriptor.of("dynamic-recurring-task", ScheduleAndNoData.class);

    private DataSource dataSource;

    public TaskManagerImpl(TaskRepository taskRepository,
                           TaskExecutionLogRepository executionLogRepository,
                           TaskConsumerRegistry consumerRegistry,
                           ExecutorService executor,
                           DataSource dataSource,
                           ObjectMapper objectMapper) {
        this.taskRepository = taskRepository;
        this.executionLogRepository = executionLogRepository;
        this.consumerRegistry = consumerRegistry;
        this.objectMapper = objectMapper;
        this.dataSource = dataSource;
        this.executor = executor;
    }

    // ---------- TaskManager 接口实现 ----------

    @Override
    public String createTask(Task task) {
        taskRepository.save(task);
        if (task.isScheduled() && task.isEnabled()) {
            scheduleRecurringTask(task);
        }
        return task.getCode();
    }

    @Override
    public void updateTask(Task task) {
        Task old = taskRepository.findByCode(task.getCode());
        if (old == null) {
            throw new IllegalArgumentException("Task not found: " + task.getCode());
        }
        taskRepository.update(task);

        // 如果原来是定时任务，先取消调度
        if (old.isScheduled()) {
            cancelScheduledTask(old.getCode());
        }

        // 如果更新后仍是启用的定时任务，重新调度
        if (task.isScheduled() && task.isEnabled()) {
            scheduleRecurringTask(task);
        }
    }

    @Override
    public void deleteTask(String code) {
        taskRepository.deleteByCode(code);
        cancelScheduledTask(code);
    }

    @Override
    public void enableTask(String code) {
        Task task = taskRepository.findByCode(code);
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + code);
        }
        if (task.isEnabled()) {
            log.info("Task already enabled: {}", code);
            return;
        }
        taskRepository.updateStatus(code, Task.STATUS_ENABLED);
        task.setStatus(Task.STATUS_ENABLED);

        if (task.isScheduled()) {
            scheduleRecurringTask(task);
        }
    }

    @Override
    public void disableTask(String code) {
        Task task = taskRepository.findByCode(code);
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + code);
        }
        taskRepository.updateStatus(code, Task.STATUS_DISABLED);
        task.setStatus(Task.STATUS_DISABLED);

        cancelScheduledTask(code);
    }

    @Override
    public TaskConsumeResult triggerTask(String code, String triggerType, Map<String, Object> triggerContext) {
        Task task = taskRepository.findByCode(code);
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + code);
        }
        if (!task.isEnabled()) {
            throw new IllegalStateException("Task is not enabled: " + code);
        }
        return executeTask(task, triggerType, triggerContext);
    }

    @Override
    public Task getTask(String code) {
        return taskRepository.findByCode(code);
    }

    @Override
    public List<Task> listEnabledTasks() {
        return taskRepository.findAllEnabled();
    }

    @Override
    public void start() {

        final RecurringTaskWithPersistentSchedule<ScheduleAndNoData> task =
                Tasks.recurringWithPersistentSchedule(DESCRIPTOR)
                        .execute((taskInstance, executionContext) -> {

                            log.debug("Instance: {},ran using ran using persistent schedule:{}", taskInstance.getId(), taskInstance.getData().getSchedule());
                            Task tk = getTask(taskInstance.getId());
                            executeTask(tk, tk.getConsumerKey(), Map.of());
                        });

        this.scheduler =
                Scheduler.create(dataSource, task)
                        .pollingInterval(Duration.ofSeconds(1))
                        .registerShutdownHook()
                        .serializer(new JacksonSerializer(objectMapper))
                        .threads(1)
                        .executorService(executor)
                        .build();
        this.scheduler.start();

        try {
            sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        loadAllScheduledTasksOnStartup();
        log.info("TaskManager scheduler started");
    }

    @Override
    public void loadAllScheduledTasksOnStartup() {
        List<Task> tasks = taskRepository.findAllEnabledScheduled();
        for (Task task : tasks) {
            try {
                scheduleRecurringTask(task);
                log.info("Loaded scheduled task on startup: {}", task.getCode());
            } catch (Exception e) {
                log.error("Failed to load scheduled task: {}", task.getCode(), e);
            }
        }
    }

    @Override
    public void stop() {
        log.info("Stopping TaskManager...");
        scheduler.stop();
        executor.shutdown();
        log.info("TaskManager stopped");
    }

    // ---------- 内部方法 ----------

    /**
     * 取消已调度的定时任务。
     */
    private void cancelScheduledTask(String taskCode) {
        try {
            TaskInstanceId instanceId = TaskInstanceId.of(
                    DbSchedulerWrapper.toTaskName(taskCode), taskCode);
            scheduler.getScheduledExecution(instanceId).ifPresent(se -> {
                scheduler.cancel(instanceId);
                log.info("Cancelled scheduled task: {}", taskCode);
            });
        } catch (Exception e) {
            log.warn("Failed to cancel scheduled task: {}", taskCode, e);
        }
    }

    /**
     * 将定时任务注册到 db-scheduler。
     * <p>
     * 使用 db-scheduler 16.x 的 RecurringTask + Schedules.cron() 模式，
     * 无需手动计算下次执行时间，由调度器自动管理 cron 调度。
     */
    private void scheduleRecurringTask(Task task) {
        String cron = extractCron(task.getParameters());
        if (cron == null || cron.isBlank()) {
            log.warn("Task {} is SCHEDULED but has no cron expression", task.getCode());
            return;
        }

        String taskCode = task.getCode();
        scheduler.schedule(
                DESCRIPTOR
                        .instance(taskCode)
                        .dataSupplier(()-> new ScheduleAndNoData(cron))
                        .scheduledAccordingToData());

        log.info("Scheduled recurring task: code={}, cron={}", task.getCode(), cron);
    }




    /**
     * 执行任务（同步执行，由调用方决定是否异步）。
     */
    private TaskConsumeResult executeTask(Task task, String triggerType, Map<String, Object> ctx) {
        TaskExecutionLog execLog = startExecutionLog(task, triggerType);
        try {
            TaskConsumer consumer = consumerRegistry.getConsumer(task.getConsumerKey());
            if (consumer == null) {
                String msg = "No consumer registered for key: " + task.getConsumerKey();
                log.error(msg);
                failExecutionLog(execLog, msg);
                return TaskConsumeResult.fail(msg);
            }

            TaskConsumeResult result = consumer.consume(task, ctx != null ? ctx : Map.of());
            if (result.isSuccess()) {
                completeExecutionLog(execLog, result.getResult());
            } else {
                failExecutionLog(execLog, result.getErrorMessage());
            }
            return result;
        } catch (Exception e) {
            log.error("Task execution failed: code={}", task.getCode(), e);
            failExecutionLog(execLog, e.getMessage());
            return TaskConsumeResult.fail(e.getMessage());
        }
    }

    /**
     * 从 parameters JSON 中提取 cron 表达式。
     * 使用 Jackson（项目已有依赖），增加容错处理。
     */
    private String extractCron(String parameters) {
        if (parameters == null || parameters.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(parameters);
            return node.has("cron") ? node.get("cron").asText() : null;
        } catch (Exception e) {
            log.warn("Failed to parse parameters JSON: {}", parameters, e);
            return null;
        }
    }

    // ---- 执行日志方法 ----

    private TaskExecutionLog startExecutionLog(Task task, String triggerType) {
        TaskExecutionLog execLog = TaskExecutionLog.builder()
                .taskCode(task.getCode())
                .consumerKey(task.getConsumerKey())
                .triggerType(triggerType)
                .status(TaskExecutionLog.LOG_STATUS_RUNNING)
                .startTime(new Date())
                .build();
        executionLogRepository.save(execLog);
        return execLog;
    }

    private void completeExecutionLog(TaskExecutionLog execLog, String result) {
        if (execLog != null && execLog.getId() != null) {
            TaskExecutionLog latest = executionLogRepository.findByLogId(execLog.getId());
            if (latest != null) {
                latest.setStatus(TaskExecutionLog.LOG_STATUS_SUCCESS);
                latest.setEndTime(new Date());
                latest.setDurationMs(System.currentTimeMillis() - latest.getStartTime().getTime());
                latest.setResult(result);
                executionLogRepository.update(latest);
            }
        }
    }

    private void failExecutionLog(TaskExecutionLog execLog, String errorMessage) {
        if (execLog != null && execLog.getId() != null) {
            TaskExecutionLog latest = executionLogRepository.findByLogId(execLog.getId());
            if (latest != null) {
                latest.setStatus(TaskExecutionLog.LOG_STATUS_FAILED);
                latest.setEndTime(new Date());
                latest.setDurationMs(System.currentTimeMillis() - latest.getStartTime().getTime());
                latest.setErrorMessage(errorMessage);
                executionLogRepository.update(latest);
            }
        }
    }

    @Data
    public static class ScheduleAndNoData implements ScheduleAndData {

        private static final long serialVersionUID = 1L; // recommended when using Java serialization

        private String cron;

        private ScheduleAndNoData() {
        }

        public ScheduleAndNoData(String cron) {
            this.cron = cron;
        }

        @Override
        public Schedule getSchedule() {
            return Schedules.cron(cron);
        }

        @Override
        public Object getData() {
            return null;
        }
    }
}

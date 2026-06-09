package com.xiaomizhou.dpsk.task.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 任务执行日志模型，对应 t_task_execution_log 表。
 *
 * @author eason - vipzhsh@163.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskExecutionLog {

    private Long id;

    /** 关联 t_task.code */
    private String taskCode;

    /** 本次执行使用的消费者标识 */
    private String consumerKey;

    /** 触发类型: SCHEDULED, MANUAL, AI_COMMAND */
    private String triggerType;

    /** 执行状态: RUNNING, SUCCESS, FAILED */
    private String status;

    private Date startTime;
    private Date endTime;
    private Long durationMs;
    private String result;
    private String errorMessage;

    // ---- 便捷常量 ----

    public static final String TRIGGER_SCHEDULED = "SCHEDULED";
    public static final String TRIGGER_MANUAL = "MANUAL";
    public static final String TRIGGER_AI_COMMAND = "AI_COMMAND";

    public static final String LOG_STATUS_RUNNING = "RUNNING";
    public static final String LOG_STATUS_SUCCESS = "SUCCESS";
    public static final String LOG_STATUS_FAILED = "FAILED";
}

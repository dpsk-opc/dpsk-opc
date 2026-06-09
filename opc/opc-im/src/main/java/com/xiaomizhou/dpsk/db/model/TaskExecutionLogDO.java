package com.xiaomizhou.dpsk.db.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaomizhou.dpsk.db.model.base.BaseModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 任务执行日志 DB 模型，对应 t_task_execution_log 表。
 *
 * @author eason - vipzhsh@163.com
 */
@EqualsAndHashCode(callSuper = true)
@TableName("t_task_execution_log")
@Data
public class TaskExecutionLogDO extends BaseModel {

    @TableField("task_code")
    private String taskCode;

    @TableField("consumer_key")
    private String consumerKey;

    @TableField("trigger_type")
    private String triggerType;

    @TableField("status")
    private String status;

    @TableField("start_time")
    private Date startTime;

    @TableField("end_time")
    private Date endTime;

    @TableField("duration_ms")
    private Long durationMs;

    @TableField("result")
    private String result;

    @TableField("error_message")
    private String errorMessage;
}

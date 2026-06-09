package com.xiaomizhou.dpsk.db.dto;

import lombok.Data;

import java.util.Date;

/**
 * 任务执行日志响应 DTO。
 *
 * @author eason - vipzhsh@163.com
 */
@Data
public class TaskExecutionLogDto {

    private Long id;

    private String taskCode;

    private String consumerKey;

    private String triggerType;

    private String status;

    private Date startTime;

    private Date endTime;

    private Long durationMs;

    private String result;

    private String errorMessage;
}

package com.xiaomizhou.dpsk.db.dto;

import lombok.Data;

/**
 * 任务响应 DTO。
 *
 * @author eason - vipzhsh@163.com
 */
@Data
public class TaskDto {

    private Long id;

    private String code;

    private String name;

    private String taskType;

    private String status;

    private String consumerKey;

    private String parameters;

    private String agentCode;

    private String conversationCode;
}

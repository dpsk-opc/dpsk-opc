package com.xiaomizhou.dpsk.db.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

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

    private AgentDto agent;

    private String conversationCode;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;
}

package com.xiaomizhou.dpsk.db.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建任务命令。
 *
 * @author eason - vipzhsh@163.com
 */
@Data
public class TaskCreateCmd {

    @NotBlank(message = "任务名称不能为空")
    private String name;

    @NotBlank(message = "任务类型不能为空")
    private String taskType;       // SCHEDULED, MANUAL, AI_COMMAND, WORKFLOW

    @NotBlank(message = "消费者标识不能为空")
    private String consumerKey;

    private String parameters;     // JSON

    private String agentCode;

    private String conversationCode;
}

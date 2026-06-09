package com.xiaomizhou.dpsk.db.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 更新任务命令。
 *
 * @author eason - vipzhsh@163.com
 */
@Data
public class TaskUpdateCmd {

    @NotBlank(message = "任务编码不能为空")
    private String code;

    private String name;

    private String taskType;

    private String consumerKey;

    private String parameters;

    private String agentCode;

    private String conversationCode;
}

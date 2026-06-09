package com.xiaomizhou.dpsk.db.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 手动触发任务参数。
 *
 * @author eason - vipzhsh@163.com
 */
@Data
public class TaskTriggerParam {

    @NotBlank(message = "任务编码不能为空")
    private String code;

    private String triggerType;    // 默认 MANUAL

    private Map<String, Object> triggerContext;
}

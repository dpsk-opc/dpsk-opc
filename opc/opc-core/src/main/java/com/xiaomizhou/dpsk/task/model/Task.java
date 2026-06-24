package com.xiaomizhou.dpsk.task.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务核心模型，对应 t_task 表。
 *
 * @author eason - vipzhsh@163.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Task {


    /** 主键 */
    private Long id;

    /** 任务编码，唯一标识 */
    private String code;

    /** 任务名称 */
    private String name;

    /** 任务类型 */
    private String taskType;

    /** 状态 */
    private String status;

    /** 消费者标识 */
    private String consumerKey;

    /** JSON 格式的参数 */
    private String parameters;

    /** 所属 Agent 编码 */
    private String agentCode;

    /** 所属会话编码 */
    private String conversationCode;

    private Integer source;

    // ---- 便捷常量 ----

    public static final String TYPE_SCHEDULED = "SCHEDULED";
    public static final String TYPE_MANUAL = "MANUAL";
    public static final String TYPE_AI_COMMAND = "AI_COMMAND";
    public static final String TYPE_WORKFLOW = "WORKFLOW";
    public static final String TYPE_TODO = "TODO";

    public static final String STATUS_ENABLED = "ENABLED";
    public static final String STATUS_DISABLED = "DISABLED";
    public static final String STATUS_PAUSED = "PAUSED";

    public static final Integer SOURCE_USER = 1;

    public static final Integer SOURCE_SYSTEM = 2;

    public static final Integer SOURCE_AGENT = 3;

    /**
     * 任务是否可用。
     */
    public boolean isEnabled() {
        return STATUS_ENABLED.equalsIgnoreCase(status);
    }

    /**
     * 是否为定时任务。
     */
    public boolean isScheduled() {
        return TYPE_SCHEDULED.equalsIgnoreCase(taskType);
    }
}

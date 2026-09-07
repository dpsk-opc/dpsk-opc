package com.xiaomizhou.dpsk.db.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaomizhou.dpsk.db.model.base.BaseModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 工作流任务实例实体类。
 * <p>
 * 状态使用 Integer 数字类型：
 * <ul>
 *   <li>0 — 待执行</li>
 *   <li>1 — 运行中</li>
 *   <li>2 — 成功</li>
 *   <li>3 — 失败</li>
 *   <li>4 — 已取消</li>
 *   <li>5 — 审批中</li>
 * </ul>
 * 来源使用 Integer 数字类型：
 * <ul>
 *   <li>0 — 用户手动</li>
 *   <li>1 — Agent</li>
 *   <li>2 — 系统</li>
 *   <li>3 — 定时任务</li>
 * </ul>
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/25
 */
@EqualsAndHashCode(callSuper = true)
@TableName("t_workflow_task")
@Data
public class WorkflowTaskDO extends BaseModel {

    /** 任务编码，唯一标识 */
    @TableField("code")
    private String code;

    /** 任务名称 */
    @TableField("name")
    private String name;

    /** 关联模板编码 */
    @TableField("template_code")
    private String templateCode;

    /** 锁定模板版本号 */
    @TableField("template_version")
    private Integer templateVersion;

    /** 状态 */
    @TableField("status")
    private Integer status;

    /** 当前执行节点ID */
    @TableField("current_node_id")
    private String currentNodeId;

    /** 当前步骤序号 */
    @TableField("current_step")
    private Integer currentStep;

    /** 执行时锁定的工作流JSON */
    @TableField("workflow_json")
    private String workflowJson;

    /** 实际输入参数JSON */
    @TableField("input_params")
    private String inputParams;

    /** 运行时上下文数据JSON */
    @TableField("context_data")
    private String contextData;

    /**
     * 任务信息
     */
    @TableField("task_info")
    private String taskInfo;

    /** 任务开始时间 */
    @TableField("start_time")
    private Date startTime;

    /** 任务结束时间 */
    @TableField("end_time")
    private Date endTime;

    /** 失败原因 */
    @TableField("error_message")
    private String errorMessage;

    /** 触发来源 */
    @TableField("source")
    private Integer source;

    @TableField("avatar")
    private String avatar;

    /** 关联会话编码 */
    @TableField("conversation_code")
    private String conversationCode;

    /** 触发Agent编码 */
    @TableField("agent_code")
    private String agentCode;

    /** 关联定时任务编码 */
    @TableField("scheduled_task_code")
    private String scheduledTaskCode;

    /** 创建者编码 */
    @TableField("owner_code")
    private String ownerCode;

    // ======================== 状态常量 ========================

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_RUNNING = 1;
    public static final int STATUS_SUCCESS = 2;
    public static final int STATUS_FAILED = 3;
    public static final int STATUS_CANCELLED = 4;
    public static final int STATUS_APPROVING = 5;

    // ======================== 来源常量 ========================

    public static final int SOURCE_USER = 0;
    public static final int SOURCE_AGENT = 1;
    public static final int SOURCE_SYSTEM = 2;
    public static final int SOURCE_SCHEDULED = 3;

    /**
     * 获取状态中文名称。
     */
    public static String statusName(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case STATUS_PENDING -> "待执行";
            case STATUS_RUNNING -> "运行中";
            case STATUS_SUCCESS -> "成功";
            case STATUS_FAILED -> "失败";
            case STATUS_CANCELLED -> "已取消";
            case STATUS_APPROVING -> "审批中";
            default -> "未知";
        };
    }

    /**
     * 获取来源中文名称。
     */
    public static String sourceName(Integer source) {
        if (source == null) return "未知";
        return switch (source) {
            case SOURCE_USER -> "用户手动";
            case SOURCE_AGENT -> "Agent";
            case SOURCE_SYSTEM -> "系统";
            case SOURCE_SCHEDULED -> "定时任务";
            default -> "未知";
        };
    }
}

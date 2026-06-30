package com.xiaomizhou.dpsk.db.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaomizhou.dpsk.db.model.base.BaseModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 工作流节点执行日志实体类。
 * <p>
 * 节点类型使用 Integer 数字类型：
 * <ul>
 *   <li>0 — 开始</li>
 *   <li>1 — 结束</li>
 *   <li>2 — LLM调用</li>
 *   <li>3 — 人工审批</li>
 *   <li>4 — 条件分支</li>
 *   <li>5 — 循环</li>
 *   <li>6 — 子工作流</li>
 * </ul>
 * 状态使用 Integer 数字类型：
 * <ul>
 *   <li>0 — 待执行</li>
 *   <li>1 — 运行中</li>
 *   <li>2 — 成功</li>
 *   <li>3 — 失败</li>
 *   <li>4 — 跳过</li>
 * </ul>
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/25
 */
@EqualsAndHashCode(callSuper = true)
@TableName("t_workflow_node_log")
@Data
public class WorkflowNodeLogDO extends BaseModel {

    /** 关联任务编码 */
    @TableField("task_code")
    private String taskCode;

    /** 节点ID */
    @TableField("node_id")
    private String nodeId;

    /** 节点名称 */
    @TableField("node_name")
    private String nodeName;

    /** 节点类型 */
    @TableField("node_type")
    private Integer nodeType;

    /** 绑定的Agent编码 */
    @TableField("agent_code")
    private String agentCode;

    /** 状态 */
    @TableField("status")
    private Integer status;

    /** 重试次数 */
    @TableField("retry_count")
    private Integer retryCount;

    /** 节点输入JSON */
    @TableField("input_data")
    private String inputData;

    /** 节点输出JSON */
    @TableField("output_data")
    private String outputData;

    /** 错误信息 */
    @TableField("error_message")
    private String errorMessage;

    /** 节点开始时间 */
    @TableField("start_time")
    private Date startTime;

    /** 节点结束时间 */
    @TableField("end_time")
    private Date endTime;

    // ======================== 节点类型常量 ========================

    public static final int TYPE_START = 0;
    public static final int TYPE_END = 1;
    public static final int TYPE_LLM = 2;
    public static final int TYPE_APPROVAL = 3;
    public static final int TYPE_CONDITION = 4;
    public static final int TYPE_LOOP = 5;
    public static final int TYPE_SUB_WORKFLOW = 6;

    // ======================== 状态常量 ========================

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_RUNNING = 1;
    public static final int STATUS_SUCCESS = 2;
    public static final int STATUS_FAILED = 3;
    public static final int STATUS_SKIPPED = 4;

    // 失败也跳过执行
    public boolean skip() {
        return STATUS_SUCCESS == status || STATUS_SKIPPED == status || STATUS_FAILED == status;
    }

    /**
     * 获取节点类型中文名称。
     */
    public static String typeName(Integer type) {
        if (type == null) return "未知";
        return switch (type) {
            case TYPE_START -> "开始";
            case TYPE_END -> "结束";
            case TYPE_LLM -> "LLM调用";
            case TYPE_APPROVAL -> "人工审批";
            case TYPE_CONDITION -> "条件分支";
            case TYPE_LOOP -> "循环";
            case TYPE_SUB_WORKFLOW -> "子工作流";
            default -> "未知";
        };
    }

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
            case STATUS_SKIPPED -> "跳过";
            default -> "未知";
        };
    }
}

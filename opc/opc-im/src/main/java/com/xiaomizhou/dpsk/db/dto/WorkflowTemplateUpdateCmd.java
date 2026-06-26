package com.xiaomizhou.dpsk.db.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 更新工作流模板命令（除 id 外全部可选）。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/25
 */
@Data
public class WorkflowTemplateUpdateCmd {

    /** 模板编码（必填） */
    @NotBlank(message = "id 不能为空")
    private String id;

    /** 模板名称 */
    private String name;

    /** 模板描述 */
    private String description;

    /** 模板分类 */
    private Integer category;

    private String avatar;

    /** 工作流DAG JSON */
    private String workflowJson;

    /** 输入参数JSON Schema */
    private String inputSchema;

    /** 超时时间（秒） */
    private Integer timeoutSeconds;

    /** 失败策略 */
    private Integer failureStrategy;

    /** 最大重试次数 */
    private Integer maxRetry;

    /** 完成时通知 */
    private Integer notifyOnComplete;

    /** 状态 */
    private Integer status;
}

package com.xiaomizhou.dpsk.db.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建工作流模板命令。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/25
 */
@Data
public class WorkflowTemplateCreateCmd {

    /** 模板名称（必填） */
    @NotBlank(message = "name 不能为空")
    private String name;

    /** 模板描述 */
    private String description;

    /** 模板分类 */
    @NotNull(message = "category 不能为空")
    private Integer category;

    /** 工作流DAG JSON（必填） */
    @NotBlank(message = "workflowJson 不能为空")
    private String workflowJson;


    /**
     * 头像
     */
    private String avatar;

    /** 输入参数JSON Schema */
    private String inputSchema;

    /** 超时时间（秒），默认 3600 */
    private Integer timeoutSeconds = 3600;

    /** 失败策略，默认 0=终止 */
    private Integer failureStrategy = 0;

    /** 最大重试次数，默认 0 */
    private Integer maxRetry = 0;

    /** 完成时通知，默认 0=否 */
    private Integer notifyOnComplete = 0;
}

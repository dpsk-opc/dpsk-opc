package com.xiaomizhou.dpsk.db.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 工作流模板响应 DTO。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/25
 */
@Data
public class WorkflowTemplateDto {

    /** 模板编码 */
    private String id;

    /** 模板名称 */
    private String name;

    /** 模板描述 */
    private String description;

    /** 模板分类 */
    private Integer category;

    /** 分类中文名称 */
    private String categoryName;

    /** 节点数量 */
    private Integer nodeCount;

    /** 状态 */
    private Integer status;

    /** 状态中文名称 */
    private String statusName;

    /** 版本号 */
    private Integer version;

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

    /** 创建者编码 */
    private String ownerCode;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;
}

package com.xiaomizhou.dpsk.tool.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具元数据，对应 t_tool 表。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/30
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolMetadata {

    /** 主键 */
    private Long id;

    /** 工具编码，唯一标识 */
    private String code;

    /** 工具名称（LLM调用用） */
    private String name;

    /** 工具描述（给LLM看） */
    private String description;

    /** 参数 JSON Schema */
    private String parametersSchema;

    /** 来源类型：LOCAL, MCP, SCRIPT */
    private String sourceType;

    /**
     * 来源引用：
     * LOCAL: beanName.methodName
     * MCP: serverId:toolName
     * SCRIPT: scriptId
     */
    private String sourceRef;

    /** 风险等级：NORMAL, DANGEROUS, ADMIN */
    private String riskLevel;

    /** 状态：ENABLED, DISABLED */
    private String status;

    /** 工具分类 */
    private String category;

    /** 标签，逗号分隔 */
    private String tags;

    /** 结果是否可缓存 */
    private Boolean cacheable;

    /** 超时毫秒 */
    private Integer timeoutMs;

    /** 所属 Agent（空为公共） */
    private String ownerAgentCode;

    // ---- 便捷常量 ----

    public static final String SOURCE_LOCAL = "LOCAL";
    public static final String SOURCE_MCP = "MCP";
    public static final String SOURCE_SCRIPT = "SCRIPT";

    public static final String RISK_NORMAL = "NORMAL";
    public static final String RISK_DANGEROUS = "DANGEROUS";
    public static final String RISK_ADMIN = "ADMIN";

    public static final String STATUS_ENABLED = "ENABLED";
    public static final String STATUS_DISABLED = "DISABLED";

    /**
     * 工具是否可用。
     */
    public boolean isEnabled() {
        return STATUS_ENABLED.equalsIgnoreCase(status);
    }

    /**
     * 是否为危险操作（需要用户确认）。
     */
    public boolean isDangerous() {
        return RISK_DANGEROUS.equalsIgnoreCase(riskLevel) || RISK_ADMIN.equalsIgnoreCase(riskLevel);
    }
}

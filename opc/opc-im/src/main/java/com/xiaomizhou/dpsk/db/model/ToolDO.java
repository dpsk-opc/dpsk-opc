package com.xiaomizhou.dpsk.db.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaomizhou.dpsk.db.model.base.BaseModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工具元数据 DB 模型，对应 t_tool 表。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/30
 */
@EqualsAndHashCode(callSuper = true)
@TableName("t_tool")
@Data
public class ToolDO extends BaseModel {

    @TableField("code")
    private String code;

    @TableField("name")
    private String name;

    @TableField("description")
    private String description;

    @TableField("parameters_schema")
    private String parametersSchema;

    @TableField("source_type")
    private String sourceType;

    @TableField("source_ref")
    private String sourceRef;

    @TableField("risk_level")
    private String riskLevel;

    @TableField("status")
    private String status;

    @TableField("category")
    private String category;

    @TableField("tags")
    private String tags;

    @TableField("cacheable")
    private Integer cacheable;

    @TableField("timeout_ms")
    private Integer timeoutMs;

    @TableField("owner_agent_code")
    private String ownerAgentCode;

    /**
     * 文件系统访问能力位：NONE / READ / WRITE
     */
    @TableField("filesystem_access")
    private String filesystemAccess;

    /**
     * 路径参数声明（JSON 数组）
     */
    @TableField("path_params")
    private String pathParams;
}

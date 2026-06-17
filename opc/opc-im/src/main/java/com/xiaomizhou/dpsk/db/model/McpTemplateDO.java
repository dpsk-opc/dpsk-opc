package com.xiaomizhou.dpsk.db.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaomizhou.dpsk.db.model.base.BaseModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MCP 服务模板 DB 模型，对应 t_mcp_template 表。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/16
 */
@EqualsAndHashCode(callSuper = true)
@TableName("t_mcp_template")
@Data
public class McpTemplateDO extends BaseModel {

    @TableField("code")
    private String code;

    @TableField("name")
    private String name;

    @TableField("description")
    private String description;

    @TableField("command")
    private String command;

    @TableField("args")
    private String args;

    @TableField("runtime_env")
    private Integer runtimeEnv;

    @TableField("runtime_available")
    private Integer runtimeAvailable;

    @TableField("tools")
    private String tools;
}

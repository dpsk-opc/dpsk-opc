package com.xiaomizhou.dpsk.db.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaomizhou.dpsk.db.model.base.BaseModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Agent MCP 绑定 DB 模型，对应 t_agent_mcp_binding 表。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/16
 */
@EqualsAndHashCode(callSuper = true)
@TableName("t_agent_mcp_binding")
@Data
public class AgentMcpBindingDO extends BaseModel {

    @TableField("code")
    private String code;

    @TableField("template_code")
    private String templateCode;

    @TableField("agent_code")
    private String agentCode;

    @TableField("enabled")
    private Integer enabled;

    @TableField("env_vars")
    private String envVars;

    @TableField("status")
    private Integer status;

    @TableField("tools_snapshot")
    private String toolsSnapshot;
}

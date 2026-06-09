package com.xiaomizhou.dpsk.db.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaomizhou.dpsk.db.model.base.BaseModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Agent与工具绑定关系 DB 模型，对应 t_agent_tool_ref 表。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/1
 */
@EqualsAndHashCode(callSuper = true)
@TableName("t_agent_tool_ref")
@Data
public class AgentToolRefDO extends BaseModel {

    @TableField("agent_code")
    private String agentCode;

    @TableField("tool_code")
    private String toolCode;
}

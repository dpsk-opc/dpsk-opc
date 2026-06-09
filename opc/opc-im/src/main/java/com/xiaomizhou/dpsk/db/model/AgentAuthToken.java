package com.xiaomizhou.dpsk.db.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaomizhou.dpsk.db.model.base.BaseModel;
import lombok.Data;

import java.util.Date;

/**
 * Agent 认证 Token 实体类
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/23
 */
@TableName("t_agent_auth_token")
@Data
public class AgentAuthToken extends BaseModel {

    /**
     * 业务编码，唯一标识一条 Token 记录
     */
    @TableField("code")
    private String code;

    /**
     * 关联的 Agent ID（对应 t_agent 表的 id）
     */
    @TableField("agent_code")
    private Long agentCode;

    /**
     * 认证 Token 字符串
     */
    @TableField("token")
    private String token;

    /**
     * Token 过期时间
     */
    @TableField("expire_time")
    private Date expireTime;

    /**
     * Token 状态: ACTIVE(有效), EXPIRED(已过期), REVOKED(已撤销)
     */
    @TableField("status")
    private String status;

}

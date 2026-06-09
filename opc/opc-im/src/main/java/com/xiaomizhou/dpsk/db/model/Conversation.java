package com.xiaomizhou.dpsk.db.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaomizhou.dpsk.db.model.base.BaseModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 会话实体类
 * 维护每个 Agent 的最近聊天列表
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/20 12:30
 */
@EqualsAndHashCode(callSuper = true)
@TableName("t_conversation")
@Data
public class Conversation extends BaseModel {

    /**
     * 会话编码，唯一标识一个会话，如 conversation_001
     */
    @TableField("code")
    private String code;

    /**
     * 所属 Agent ID (t_agent.code)
     */
    @TableField("owner_code")
    private String ownerCode;

    /**
     * 会话类型: 0-SINGLE(单聊), 1-GROUP(群聊)
     */
    @TableField("conversation_type")
    private Integer conversationType;

    /**
     * 对方ID: 单聊时为对方agent.code, 群聊时为group.code
     */
    @TableField("target_code")
    private String targetCode;

    /**
     * 最后一条消息ID
     */
    @TableField("last_message_code")
    private String lastMessageCode;

    /**
     * 最后一条消息预览（前200字符）
     */
    @TableField("last_message_content")
    private String lastMessageContent;

    /**
     * 最后一条消息时间
     */
    @TableField("last_message_time")
    private Date lastMessageTime;

    /**
     * 最后一条消息发送者ID
     */
    @TableField("last_sender_code")
    private String lastSenderCode;

    /**
     * 是否置顶: 0=否, 1=是
     */
    @TableField("is_top")
    private Integer isTop;

    /**
     * 扩展配置（JSON），如免打扰等
     */
    @TableField("ext_config")
    private String extConfig;

}

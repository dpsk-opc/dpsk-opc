package com.xiaomizhou.dpsk.db.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Token用量响应 DTO（列表与详情共用）
 *
 * @author eason - vipzhsh@163.com
 */
@Data
public class TokenUsageDto {

    private Long id;

    private String code;

    /** 所属Agent编码 */
    private String agentCode;

    /** 关联会话编码 */
    private String conversationCode;

    /** 关联消息编码 */
    private String messageCode;

    /** 关联任务ID */
    private String taskId;

    // ==================== Token用量详情 ====================

    /** 输入Token数量 */
    private Integer inputTokens;

    /** 输出Token数量 */
    private Integer outputTokens;

    /** 总Token数量 */
    private Integer totalTokens;

    /** 估算费用 */
    private BigDecimal cost;

    /** 用量类型: CHAT, TASK, EMBEDDING, OTHER */
    private String usageType;

    /** 模型名称 */
    private String modelName;

    /** 模型提供商 */
    private String provider;

    /** 扩展配置 */
    private String extConfig;

    // ==================== 关联数据 ====================

    /** 所属Agent信息 */
    private AgentDto agent;

    /** 关联会话信息 */
    private ConversationDto conversation;

    /** 关联消息信息 */
    private ChatMsgDto message;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;
}

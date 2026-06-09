package com.xiaomizhou.dpsk.db.model;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaomizhou.dpsk.db.model.base.BaseModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/20 14:37
 * @description Token用量表，记录每个Agent/任务的Token使用情况
 */
@TableName("t_token_usage")
@Data
@EqualsAndHashCode(callSuper = true)
public class TokenUsage extends BaseModel {

    // ==================== 业务字段 ====================
    /**
     * 业务编码，唯一标识一条用量记录
     */
    private String code;

    /**
     * 所属Agent ID (t_agent.code)
     */
    private String agentCode;

    /**
     * 关联的会话ID (t_conversation.code)，可为null
     */
    private String conversationCode;

    /**
     * 关联的消息ID (t_message.code)，可为null
     */
    private String messageCode;

    /**
     * 关联的任务ID，用于追踪Agent任务
     */
    private String taskId;

    // ==================== Token用量详情 ====================
    /**
     * 输入Token数量（用户消息、提示词等）
     */
    private Integer inputTokens;

    /**
     * 输出Token数量（Agent回复内容）
     */
    private Integer outputTokens;

    /**
     * 总Token数量（input+output）
     */
    private Integer totalTokens;

    /**
     * 估算费用（美元或自定义单位）
     */
    private BigDecimal cost;

    // ==================== 使用场景 ====================
    /**
     * 用量类型: CHAT(聊天), TASK(任务), EMBEDDING(向量化), OTHER(其他)
     */
    private String usageType;

    /**
     * 使用的模型名称，如 gpt-4, claude-3, deepseek-chat
     */
    private String modelName;

    /**
     * 模型提供商: OPENAI, ANTHROPIC, DEEPSEEK, OLLAMA 等
     */
    private String provider;

    // ==================== 扩展配置 ====================
    /**
     * 扩展配置（JSON），如存储原始响应、请求参数等
     */
    private String extConfig;
}

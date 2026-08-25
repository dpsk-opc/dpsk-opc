package com.xiaomizhou.dpsk.db.dto;

import dev.langchain4j.model.output.TokenUsage;
import lombok.Builder;
import lombok.Data;


@Data
@Builder
public class UsageRecord {

    /** 产生用量的 Agent code（如规划器/分类器自身、或所属 Agent） */
    private String agentCode;
    /** 关联会话 code */
    private String conversationCode;
    /** 关联消息 code */
    private String messageCode;
    /** 关联任务 code */
    private String taskId;
    /** 用量类型：CHAT / TASK / OTHER 等，对应 t_token_usage.usage_type */
    private String usageType;
    /** 模型名称 */
    private String modelName;
    /** 模型提供商 */
    private String provider;
    /** langchain4j 返回的 token 用量（必填，否则不落库） */
    private TokenUsage tokenUsage;

}

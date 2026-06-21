package com.xiaomizhou.dpsk.db.dto;

import lombok.Data;

/**
 * Token用量分页查询参数
 *
 * @author eason - vipzhsh@163.com
 */
@Data
public class TokenUsagePageCmd {

    /** 用量类型: CHAT, TASK, EMBEDDING, OTHER */
    private String usageType;

    /** 模型名称 */
    private String modelName;

    /** 模型提供商 */
    private String provider;

    /** 关联会话编码 */
    private String conversationCode;

    /** 关联消息编码 */
    private String messageCode;

    private Integer pageNo = 1;

    private Integer pageSize = 10;
}

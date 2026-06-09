package com.xiaomizhou.dpsk.memory.model;

import java.time.Instant;

/**
 * L1 摘要记忆模型，对应 t_memory_summary 表。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/29
 */
public class MemorySummary {

    /** 主键ID */
    private Long id;

    /** 摘要编码，唯一标识 */
    private String code;

    /** 所属 Agent code */
    private String ownerCode;

    /** 所属会话 code */
    private String conversationCode;

    /** 累积摘要文本（Agent第一人称） */
    private String summaryText;

    /** 摘要覆盖的首条消息code */
    private String startMessageCode;

    /** 摘要覆盖的末条消息code */
    private String endMessageCode;

    /** 生成时间 */
    private Instant createTime;

    /** 逻辑删除 */
    private Integer isDeleted;

    public MemorySummary() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getOwnerCode() { return ownerCode; }
    public void setOwnerCode(String ownerCode) { this.ownerCode = ownerCode; }

    public String getConversationCode() { return conversationCode; }
    public void setConversationCode(String conversationCode) { this.conversationCode = conversationCode; }

    public String getSummaryText() { return summaryText; }
    public void setSummaryText(String summaryText) { this.summaryText = summaryText; }

    public String getStartMessageCode() { return startMessageCode; }
    public void setStartMessageCode(String startMessageCode) { this.startMessageCode = startMessageCode; }

    public String getEndMessageCode() { return endMessageCode; }
    public void setEndMessageCode(String endMessageCode) { this.endMessageCode = endMessageCode; }

    public Instant getCreateTime() { return createTime; }
    public void setCreateTime(Instant createTime) { this.createTime = createTime; }

    public Integer getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Integer isDeleted) { this.isDeleted = isDeleted; }
}

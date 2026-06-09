package com.xiaomizhou.dpsk.memory.model;

import java.time.Instant;

/**
 * L2 长期事实模型，对应 t_long_term_fact 表。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/29
 */
public class LongTermFact {

    /** 主键ID */
    private Long id;

    /** 事实编码，唯一标识 */
    private String code;

    /** 发现该事实的 Agent code */
    private String ownerCode;

    /** 事实关联的目标（用户/群组）code */
    private String targetCode;

    /** 事实类型：PREFERENCE, EVENT, RELATION */
    private String factType;

    /** 事实自然语言描述 */
    private String factContent;

    /** 重要性 0-1 */
    private Float importance;

    /** 创建时间 */
    private Instant createTime;

    /** 最后访问时间 */
    private Instant lastAccessedTime;

    /** 逻辑删除 */
    private Integer isDeleted;

    public LongTermFact() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getOwnerCode() { return ownerCode; }
    public void setOwnerCode(String ownerCode) { this.ownerCode = ownerCode; }

    public String getTargetCode() { return targetCode; }
    public void setTargetCode(String targetCode) { this.targetCode = targetCode; }

    public String getFactType() { return factType; }
    public void setFactType(String factType) { this.factType = factType; }

    public String getFactContent() { return factContent; }
    public void setFactContent(String factContent) { this.factContent = factContent; }

    public Float getImportance() { return importance; }
    public void setImportance(Float importance) { this.importance = importance; }

    public Instant getCreateTime() { return createTime; }
    public void setCreateTime(Instant createTime) { this.createTime = createTime; }

    public Instant getLastAccessedTime() { return lastAccessedTime; }
    public void setLastAccessedTime(Instant lastAccessedTime) { this.lastAccessedTime = lastAccessedTime; }

    public Integer getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Integer isDeleted) { this.isDeleted = isDeleted; }
}

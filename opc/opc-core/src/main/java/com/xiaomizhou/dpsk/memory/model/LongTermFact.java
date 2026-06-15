package com.xiaomizhou.dpsk.memory.model;

import lombok.Data;

import java.time.Instant;

/**
 * L2 长期事实模型，对应 t_long_term_fact 表。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/29
 */
@Data
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

    /**
     * 状态：0-未向量化，1-已向量化
     */
    private Integer status;

    /** 创建时间 */
    private Instant createTime;

    /** 最后访问时间 */
    private Instant lastAccessedTime;

    /** 逻辑删除 */
    private Integer isDeleted;
}

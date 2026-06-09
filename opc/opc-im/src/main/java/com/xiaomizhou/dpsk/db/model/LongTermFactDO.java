package com.xiaomizhou.dpsk.db.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaomizhou.dpsk.db.model.base.BaseModel;
import lombok.Data;

import java.util.Date;

/**
 * L2 长期事实表 DB 模型，对应 t_long_term_fact。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/29
 */
@Data
@TableName("t_long_term_fact")
public class LongTermFactDO extends BaseModel {

    @TableField("code")
    private String code;

    @TableField("owner_code")
    private String ownerCode;

    @TableField("target_code")
    private String targetCode;

    @TableField("fact_type")
    private String factType;

    @TableField("fact_content")
    private String factContent;

    @TableField("importance")
    private Float importance;

    @TableField("last_accessed_time")
    private Date lastAccessedTime;
}

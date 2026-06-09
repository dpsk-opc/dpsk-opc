package com.xiaomizhou.dpsk.db.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaomizhou.dpsk.db.model.base.BaseModel;
import lombok.Data;

/**
 * L1 摘要记忆表 DB 模型，对应 t_memory_summary。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/29
 */
@Data
@TableName("t_memory_summary")
public class MemorySummaryDO extends BaseModel {

    @TableField("code")
    private String code;

    @TableField("owner_code")
    private String ownerCode;

    @TableField("conversation_code")
    private String conversationCode;

    @TableField("summary_text")
    private String summaryText;

    @TableField("start_message_code")
    private String startMessageCode;

    @TableField("end_message_code")
    private String endMessageCode;
}

package com.xiaomizhou.dpsk.db.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaomizhou.dpsk.db.model.base.BaseModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 任务 DB 模型，对应 t_task 表。
 *
 * @author eason - vipzhsh@163.com
 */
@EqualsAndHashCode(callSuper = true)
@TableName("t_task")
@Data
public class TaskDO extends BaseModel {

    @TableField("code")
    private String code;

    @TableField("name")
    private String name;

    @TableField("task_type")
    private String taskType;

    @TableField("status")
    private String status;

    @TableField("consumer_key")
    private String consumerKey;

    @TableField("parameters")
    private String parameters;

    @TableField("agent_code")
    private String agentCode;

    @TableField("conversation_code")
    private String conversationCode;

    /**
     * 来源：1-user，2-系统，3-agent，4-专家团
     */
    @TableField("source")
    private Integer source;
}

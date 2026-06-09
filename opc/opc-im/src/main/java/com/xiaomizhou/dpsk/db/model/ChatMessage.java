package com.xiaomizhou.dpsk.db.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaomizhou.dpsk.db.model.base.BaseModel;
import lombok.Data;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/18 18:06
 * @description
 */
@Data
@TableName("t_chat_message")
public class ChatMessage extends BaseModel {

    @TableField("code")
    private String code;

    @TableField("conversation_type")
    private String conversationType;   // SINGLE, GROUP

    @TableField("sender_code")
    private String senderCode;

    @TableField("receiver_code")
    private String receiverCode;

    /**
     * 消息类型: TEXT(文本), COMMAND(命令), TASK_RESULT(任务结果),USER(用户消息),AI(AI消息),TOOL(工具信息)
     */
    @TableField("message_type")
    private String messageType;

    @TableField("content")
    private String content;

    /**
     * 0-TEXT
     */
    @TableField("content_type")
    private Integer contentType;

    /**
     * 备注信息
     */
    @TableField("remark")
    private String remark;

    @TableField("task_id")
    private String taskId;

    @TableField("parent_id")
    private Long parentId;             // 引用的消息ID，0表示无引用

    /**
     * @提及的Agent ID列表，JSON数组字符串，如 "[101,102,103]"
     * 建议应用层使用 Jackson/Gson 进行 List<Long> 与 String 的转换
     */
    @TableField("mentioned_list")
    private String mentionedList;

    @TableField("status")
    private String status;             // SENDING, SENT, DELIVERED, FAILED
}



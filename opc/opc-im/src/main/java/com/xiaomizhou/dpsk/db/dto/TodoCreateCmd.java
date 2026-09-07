package com.xiaomizhou.dpsk.db.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Date;

/**
 * 创建待办事项命令。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/24
 */
@Data
public class TodoCreateCmd {


    private String agentId;

    /** 联系人 Agent Code（必填） */
    @NotBlank(message = "refCode 不能为空")
    private String refCode;

    @NotBlank(message = "refType不能为空")
    private Integer refType;

    /** 待办名称（必填） */
    @NotBlank(message = "title 不能为空")
    private String title;

    /** 待办内容（必填） */
    @NotBlank(message = "content 不能为空")
    private String content;

    /** 逾期时间，ISO 8601 格式（必填） */
    @NotBlank(message = "dueTime 不能为空")
    private Date dueTime;

    /** 闹铃文件 URL，null=默认铃声 */
    private String alarmSound;

    /** 是否开启提醒，默认 false */
    private Boolean alarmEnabled = false;

    /** 状态: pending / in_progress / done，默认 pending */
    private Integer status = 0;

    /** 所属会话编码 */
    private String conversationCode;
}

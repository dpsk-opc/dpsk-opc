package com.xiaomizhou.dpsk.db.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 待办事项响应 DTO。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/24
 */
@Data
public class TodoItemDto {

    /** 待办编码 */
    private String id;

    /** 联系人 Agent Code */
    private String agentId;

    /** 待办名称 */
    private String title;

    /** 待办内容 */
    private String content;

    /** 逾期时间（ISO 8601） */
    private Date dueTime;

    /** 闹铃文件 URL，null=默认铃声 */
    private String alarmSound;

    /** 是否开启提醒 */
    private Boolean alarmEnabled;

    /** 状态: pending / in_progress / done */
    private Integer status;

    /** 创建时间（ISO 8601） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    /** 更新时间（ISO 8601） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;
}

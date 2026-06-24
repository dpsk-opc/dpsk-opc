package com.xiaomizhou.dpsk.db.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Date;

/**
 * 更新待办事项命令（除 id 外全部可选）。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/24
 */
@Data
public class TodoUpdateCmd {

    /** 待办 ID（必填） */
    @NotBlank(message = "id 不能为空")
    private String id;

    /** 待办名称 */
    private String title;

    /** 待办内容 */
    private String content;

    /** 逾期时间，ISO 8601 格式 */
    private Date dueTime;

    /** 闹铃文件 URL */
    private String alarmSound;

    /** 是否开启提醒 */
    private Boolean alarmEnabled;

    /** 状态: pending / in_progress / done */
    private Integer status;
}

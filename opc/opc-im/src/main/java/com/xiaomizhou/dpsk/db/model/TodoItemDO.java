package com.xiaomizhou.dpsk.db.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaomizhou.dpsk.db.model.base.BaseModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 待办事项实体类。
 * <p>
 * 状态使用 Integer 数字类型：
 * <ul>
 *   <li>0 — pending（待办）</li>
 *   <li>1 — in_progress（进行中）</li>
 *   <li>2 — done（已完成）</li>
 * </ul>
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/24
 */
@EqualsAndHashCode(callSuper = true)
@TableName("t_todo_item")
@Data
public class TodoItemDO extends BaseModel {

    public static final Integer REF_TYPE_AGENT = 0;

    public static final Integer REF_TYPE_WORKFLOW = 1;

    /** 待办编码，唯一标识 */
    @TableField("code")
    private String code;

    /** 关联的联系人 Agent Code */
    @TableField("agent_code")
    @Deprecated
    private String agentCode;

    /** 关联的类型：0: agent / 1-workflow */
    @TableField("ref_type")
    private Integer refType;

    /** 关联的编码 */
    @TableField("ref_code")
    private String refCode;

    /** 所属用户 Agent Code（创建者） */
    @TableField("owner_code")
    private String ownerCode;

    /** 待办名称 */
    @TableField("title")
    private String title;

    /** 待办内容 */
    @TableField("content")
    private String content;

    /** 逾期时间 */
    @TableField("due_time")
    private Date dueTime;

    /** 闹铃文件 URL，NULL=默认铃声 */
    @TableField("alarm_sound")
    private String alarmSound;

    /** 是否开启提醒: 0=关闭, 1=开启 */
    @TableField("alarm_enabled")
    private Integer alarmEnabled;

    /** 关联的任务编码 */
    @TableField("task_code")
    private String taskCode;

    /** 关联的会话编码 */
    @TableField("conversation_code")
    private String conversationCode;

    /**
     * 状态: 0=pending, 1=in_progress, 2=done
     */
    @TableField("status")
    private Integer status;

    // ======================== 状态常量 ========================

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_IN_PROGRESS = 1;
    public static final int STATUS_DONE = 2;

    // ======================== 状态工具方法 ========================
}

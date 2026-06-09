package com.xiaomizhou.dpsk.db.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaomizhou.dpsk.db.model.base.BaseModel;
import lombok.Data;

import java.util.Date;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/18 18:31
 * @description 群组成员关系表
 */
@TableName("t_chat_group_member")
@Data
public class ChatGroupMember extends BaseModel {

    @TableField("chat_group_code")
    private String chatGroupCode;

    @TableField("agent_code")
    private String agentCode;

    @TableField("role")
    private String role;               // OWNER, ADMIN, MEMBER

    @TableField("nickname_in_group")
    private String nicknameInGroup;

    @TableField("join_time")
    private Date joinTime;

    @TableField("status")
    private Integer status;            // 0-ACTIVE, 1-QUIT, 2-KICKED

}

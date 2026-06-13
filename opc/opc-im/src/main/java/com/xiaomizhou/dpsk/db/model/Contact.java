package com.xiaomizhou.dpsk.db.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaomizhou.dpsk.db.model.base.BaseModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 好友关系实体类
 * 维护用户之间的好友关系（双向关系，两条记录分别表示各自的好友列表）
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/11
 */
@EqualsAndHashCode(callSuper = true)
@TableName("t_contact")
@Data
public class Contact extends BaseModel {

    /**
     * 好友关系编码，唯一标识
     */
    @TableField("code")
    private String code;

    /**
     * 所属用户 Agent Code（这条好友记录属于谁）
     */
    @TableField("owner_code")
    private String ownerCode;

    /**
     * 好友的 Agent Code
     */
    @TableField("friend_code")
    private String friendCode;

    /**
     * 备注名
     */
    @TableField("remark")
    private String remark;

    /**
     * 状态: ACTIVE(正常), BLOCKED(已拉黑)
     */
    @TableField("status")
    private String status;

}

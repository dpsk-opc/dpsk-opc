package com.xiaomizhou.dpsk.db.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaomizhou.dpsk.db.model.base.BaseModel;
import lombok.Data;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/18 18:31
 * @description
 */
@TableName("t_chat_group")
@Data
public class ChatGroup extends BaseModel {

    @TableField("code")
    private String code;

    @TableField("name")
    private String name;

    @TableField("avatar")
    private String avatar;

    @TableField("owner_code")
    private String ownerCode;

    @TableField("announcement")
    private String announcement;

    @TableField("status")
    private Integer status;          // 0-ACTIVE,1-DISBANDED

    // 冗余字段：最后一条消息信息
    @TableField("last_message_code")
    private String lastMessageCode;

    @TableField("ext_config")
    private String extConfig;       // JSON 字符串

    /**
     * 群工作空间（公共产出目录）：全员可见可写，不按成员划分子目录。
     */
    @TableField("workspace")
    private String workspace;

}

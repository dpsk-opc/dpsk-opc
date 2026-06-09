package com.xiaomizhou.dpsk.db.model.base;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/18 17:55
 * @description
 */
@Data
public abstract class BaseModel {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @TableLogic
    @TableField("is_deleted")
    private Integer isDeleted = 0;         // 0=未删除, 1=已删除


}

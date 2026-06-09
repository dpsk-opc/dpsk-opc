package com.xiaomizhou.dpsk.db.dto;

import lombok.Data;

/**
 * 群组列表查询参数
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/22
 */
@Data
public class GroupListCmd {

    /**
     * 群组名称（模糊查询）
     */
    private String name;

}

package com.xiaomizhou.dpsk.db.dto;

import lombok.Data;

/**
 * 更新群组请求参数
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/21
 */
@Data
public class GroupUpdateCmd {

    /**
     * 群组编码
     */
    private String groupCode;

    /**
     * 群组名称
     */
    private String name;

    /**
     * 群组头像
     */
    private String avatar;

    /**
     * 群工作空间（公共产出目录），可选；传 null 表示不修改
     */
    private String workspace;

}

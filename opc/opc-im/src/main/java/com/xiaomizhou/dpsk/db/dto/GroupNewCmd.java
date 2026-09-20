package com.xiaomizhou.dpsk.db.dto;

import lombok.Data;

import java.util.List;

/**
 * 创建群组请求参数
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/21
 */
@Data
public class GroupNewCmd {

    /**
     * 群组名称（可选，不传则自动拼接前3个成员名称）
     */
    private String name;

    /**
     * 群组头像
     */
    private String avatar;

    /**
     * 群主编码
     */
    private String ownerCode;

    /**
     * 成员编码列表
     */
    private List<String> memberCodes;

    /**
     * 群工作空间（公共产出目录），可选；不传则使用系统默认值
     */
    private String workspace;

}

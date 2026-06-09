package com.xiaomizhou.dpsk.db.dto;

import lombok.Data;

/**
 * Agent 查询参数对象
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/18
 */
@Data
public class AgentQueryParam {

    /**
     * 业务编码
     */
    private String code;

    /**
     * 名称（模糊查询）
     */
    private String name;

    /**
     * 昵称（模糊查询）
     */
    private String nickname;

    /**
     * 角色
     */
    private String role;

    /**
     * MBTI类型
     */
    private String mbti;

    /**
     * 性别: 0=未知, 1=男, 2=女
     */
    private Integer sex;

    /**
     * 类型
     */
    private String type;

    /**
     * 状态
     */
    private String status;

    /**
     * 关键词搜索（名称、昵称、描述）
     */
    private String keyword;

    /**
     * 页码
     */
    private int pageNo = 1;

    /**
     * 每页数量
     */
    private int pageSize = 10;
}

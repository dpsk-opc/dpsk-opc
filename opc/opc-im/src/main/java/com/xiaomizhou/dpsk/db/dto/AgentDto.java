package com.xiaomizhou.dpsk.db.dto;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * Agent 数据传输对象
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/18
 */
@Data
public class AgentDto {

    private Long id;

    /**
     * 业务编码
     */
    private String code;

    /**
     * 名称
     */
    private String name;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 个性签名
     */
    private String slogan;

    /**
     * 性别: 0=未知, 1=男, 2=女
     */
    private Integer sex;

    /**
     * MBTI类型，如: INFJ
     */
    private String mbti;

    /**
     * Agent的prompt
     */
    private String prompt;

    /**
     * 工作目录
     */
    private String workspace;

    /**
     * 角色
     */
    private String role;

    /**
     * 描述
     */
    private String description;

    /**
     * 类型: USER(真实用户), AGENT-LOCAL(AI智能体), SYSTEM(系统), AGENT-A2A(远程AI智能体)
     */
    private String type;

    /**
     * 头像
     */
    private String avatar;

    /**
     * 状态: ACTIVE(活跃), INACTIVE(停用), DELETING(删除中)
     */
    private String status;

    /**
     * 集成配置（JSON字符串）
     */
    private String integrationConfig;

    /**
     * LLM配置（JSON字符串），非必填，为空时使用系统默认配置
     */
    private String llmConfig;

    /**
     * 最后活跃时间
     */
    private Date lastActiveTime;

    /**
     * 部门
     */
    private String department;

    /**
     * 工具列表
     */
    private List<AgentToolRefVO> tools;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;
}

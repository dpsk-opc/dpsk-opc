package com.xiaomizhou.dpsk.db.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Agent 创建命令对象
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/18
 */
@Data
public class AgentCreateCmd {

    /**
     * 业务编码
     */
    private String code;

    /**
     * 名称
     */
    @NotBlank(message = "名称不能为空")
    private String name;

    /**
     * 昵称
     */
    private String nickname;

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
     * 类型: USER, AGENT-LOCAL, SYSTEM, AGENT-A2A
     */
    @NotNull(message = "类型不能为空")
    private String type;

    /**
     * 头像
     */
    private String avatar;

    /**
     * 集成配置（JSON字符串）
     */
    private String integrationConfig;

    /**
     * LLM配置（JSON字符串），非必填
     * 示例: {"model":"gpt-3.5-turbo","access_key":"xxx","temperature":0.7,"max_tokens":1024}
     */
    private String llmConfig;

    /**
     *  模态: 0-TEXT, 1-IMAGE, 2-VIDEO, 3-AUDIO, 4-MIXED
     */
    private Integer modality;

    /**
     *  工具
     */
    private List<String> tools;
}

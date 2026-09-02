package com.xiaomizhou.dpsk.db.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;


/**
 * Agent 更新命令对象
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/18
 */
@Data
public class AgentUpdateCmd {

    /**
     * 业务编码
     */
    @NotNull(message = "Code不能为空")
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
     * 类型
     */
    private String type;

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
     * 头像
     */
    private String avatar;

    /**
     * 状态: ACTIVE, INACTIVE, DELETING
     */
    private String status;

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
     * 模态: 0-TEXT, 1-IMAGE, 2-VIDEO, 3-AUDIO, 4-MIXED
     */
    private Integer modality;

    /**
     * 能力标签列表，可选。为空时由 LLM 从 prompt 提取。
     */
    private List<String> capabilities;
}

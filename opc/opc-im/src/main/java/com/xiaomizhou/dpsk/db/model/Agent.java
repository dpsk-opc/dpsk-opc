package com.xiaomizhou.dpsk.db.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaomizhou.dpsk.db.model.base.BaseModel;
import lombok.Data;

import java.util.Date;

/**
 * Agent 实体类
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/18 17:40
 */
@TableName("t_agent")
@Data
public class Agent extends BaseModel {

    @TableField("code")
    private String code;

    @TableField("name")
    private String name;

    @TableField("nickname")
    private String nickname;

    /**
     * 邮箱（登录账号）
     */
    @TableField("email")
    private String email;

    /**
     * 密码（BCrypt 加密）
     */
    @TableField("password")
    private String password;

    /**
     * 性别: 0=未知, 1=男, 2=女
     */
    @TableField("sex")
    private Integer sex;

    /**
     * 个性签名
     */
    @TableField("slogan")
    private String slogan;

    /**
     * MBTI类型，如: INFJ
     */
    @TableField("mbti")
    private String mbti;

    /**
     * Agent的prompt
     */
    @TableField("prompt")
    private String prompt;

    /**
     * 工作目录
     */
    @TableField("workspace")
    private String workspace;

    /**
     * 角色
     */
    @TableField("role")
    private String role;

    @TableField("description")
    private String description;

    /**
     * 类型: USER(真实用户), AGENT-LOCAL(AI智能体), SYSTEM(系统), AGENT-A2A(远程AI智能体)
     */
    @TableField("type")
    private String type;

    @TableField("avatar")
    private String avatar;

    /**
     * 状态: ACTIVE(活跃), INACTIVE(停用), DELETING(删除中)
     */
    @TableField("status")
    private String status;

    /**
     * 集成配置（JSON字符串），示例: {"protocol":"HTTP","endpoint":"https://api.example.com","auth":{"type":"BEARER","token":"xxx"},"capabilities":["text","image"]}
     */
    @TableField("integration_config")
    private String integrationConfig;

    /**
     * LLM配置（JSON字符串），示例: {"model":"gpt-3.5-turbo","access_key":"xxx","temperature":0.7,"max_tokens":1024,"top_p":0.9}
     * 非必填，为空时使用系统默认 LLM 配置
     */
    @TableField("llm_config")
    private String llmConfig;

    /**
     * 能力标签（JSON数组字符串），如 ["代码开发","数据分析","写作","翻译"]。
     * 由 LLM 从 prompt 提取，供群聊 Picker 做能力匹配。
     */
    @TableField("capabilities")
    private String capabilities;

    /**
     * 最后活跃时间（登录或收发消息时间）
     */
    @TableField("last_active_time")
    private Date lastActiveTime;

    /**
     * 模态: 0-TEXT, 1-IMAGE, 2-VIDEO, 3-AUDIO, 4-MIXED
     */
    @TableField("modality")
    private Integer modality;

}

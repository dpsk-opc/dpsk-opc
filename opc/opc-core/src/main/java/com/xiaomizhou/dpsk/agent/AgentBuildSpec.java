package com.xiaomizhou.dpsk.agent;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Agent 构建规范，由 opc-im 组装后传给 opc-core。
 * 所有聊天模式（single/group/workflow）统一使用此输入格式。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/3
 */
@Data
@Builder
public class AgentBuildSpec {

    /** 单聊模式 */
    public static final String MODE_SINGLE = "SINGLE";

    /** 群聊模式 */
    public static final String MODE_GROUP = "GROUP";

    /** 工作流模式（预留） */
    public static final String MODE_WORKFLOW = "WORKFLOW";

    /** 运行模式：SINGLE / GROUP / WORKFLOW */
    private String mode;

    /** 会话编码 */
    private String conversationCode;

    /** 当前用户编码 */
    private String userCode;

    /** 用户当前消息内容 */
    private String userContent;

    /** 目标 Agent（单聊时使用） */
    private String targetAgentCode;

    /** 目标 Agent 列表（群聊时使用，含 Agent 编码） */
    private List<String> targetAgentCodes;

    /** 群组编码（群聊时使用） */
    private String groupCode;

    /** 引用消息编码（@ 功能） */
    private String quoteMessageCode;

    /**
     * MCP 编码列表（工作流模式时使用）
     */
    private List<String> mcpCodes;

    /** 扩展参数（透传给 Builder） */
    private Map<String, Object> params;

    /**
     * 定时任务触发时的锚点上下文字符串。
     * <p>
     * 由 AgentTaskConsumer 在任务触发时根据 anchorMsgCode 查库拼装，
     * 注入到 system prompt 末尾，帮助 AI 还原"创建任务时的对话上下文"。
     * 非定时任务场景下为空。
     */
    private String taskContext;
}

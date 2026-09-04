package com.xiaomizhou.dpsk.workflow.support;

import com.xiaomizhou.dpsk.agent.AgentBuildSpec;
import com.xiaomizhou.dpsk.agent.PipelineResult;
import com.xiaomizhou.dpsk.constant.ConversationType;
import com.xiaomizhou.dpsk.db.chat.ImAgentCallback;
import com.xiaomizhou.dpsk.utils.SequenceUtils;
import com.xiaomizhou.dpsk.workflow.NodeContext;
import com.xiaomizhou.dpsk.workflow.WorkflowContext;

/**
 * Agent 调用封装：统一构建 {@link ImAgentCallback} + {@link AgentBuildSpec} 并执行。
 *
 * <p>供 Agent / Switch 等需要调用 Agent 的节点复用，消除重复代码。
 */
public final class AgentInvoker {

    private AgentInvoker() {
    }

    /**
     * 调用 Agent。
     *
     * @param wf          任务上下文
     * @param node        当前节点配置
     * @param userContent 输入给 Agent 的内容
     * @param senderInfo  Agent 编码（用于推送消息的发送者信息），为 null 时不设置
     */
    public static PipelineResult invoke(WorkflowContext wf, NodeContext node, String userContent, String senderInfo) {
        // 按会话类型切消息归属目标：
        //  - GROUP：conversationType=GROUP、targetId=groupCode → thinking/消息落群会话
        //  - WORKFLOW：保持现状
        String conversationType = wf.getConversationType() != null ? wf.getConversationType() : ConversationType.WORKFLOW.name();
        String targetId = ConversationType.GROUP.name().equalsIgnoreCase(conversationType)
                ? (wf.getGroupCode() != null ? wf.getGroupCode() : wf.getTargetId())
                : wf.getTargetId();

        ImAgentCallback callback = new ImAgentCallback(
                wf.getUserId(), wf.getConversationCode(),
                conversationType, targetId, wf.getTaskId(),
                wf.getChatMessageComponent(), wf.getTokenUsageDao(), wf.getAgentDefProvider());
        callback.setStreamCode(SequenceUtils.generator().next("STM"));
        callback.setCancelFlag(wf.getCancelFlag());
        if (senderInfo != null) {
            callback.setSenderInfo(senderInfo);
        }

        String taskDetail = """
                任务详情： [%s]
                
                你的任务输入：[%s]
                """.formatted(wf.getContextData(), userContent);

        AgentBuildSpec spec = AgentBuildSpec.builder()
                .mode(AgentBuildSpec.MODE_WORKFLOW)
                .userCode(wf.getUserId())
                .targetAgentCode(node.getAgentCode())
                .userContent(taskDetail)
                // 锚定触发本工作流的原始用户消息，L0 记忆在 UserMessage 被工具消息挤出时按此 code 精确取回
                .userMessageCode(wf.getUserMessageCode())
                .conversationCode(wf.getConversationCode())
                .mcpCodes(node.getMcpCodes())
                .skillPaths(node.getSkillPaths())
                .taskCode(wf.getTaskId())
                .prompt(node.getPrompt())
                .build();

        return wf.getOrchestrator().execute(spec, callback);
    }
}

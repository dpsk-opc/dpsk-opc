package com.xiaomizhou.dpsk.workflow;

import com.xiaomizhou.dpsk.agent.AgentOrchestrator;
import com.xiaomizhou.dpsk.agent.data.AgentDefProvider;
import com.xiaomizhou.dpsk.db.ChatMessageComponent;
import com.xiaomizhou.dpsk.db.WorkflowTaskComponent;
import com.xiaomizhou.dpsk.db.WorkflowTaskExecuteComponent;
import com.xiaomizhou.dpsk.db.dao.TokenUsageDao;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Builder
@Getter
public class WorkflowContext {

    private String userId;

    private String targetId;

    private String taskId;

    private Map<String, NodeContext> nodes;

    private String contextData;

    private String conversationCode;

    /** 会话类型：GROUP / WORKFLOW，AgentInvoker 据此切消息归属目标 */
    private String conversationType;

    /** 群聊时的群编码（消息归属目标），非群聊为 null */
    private String groupCode;

    /** nodeId → 节点输出文本，每节点执行完累积（replan 输入） */
    @Builder.Default
    private Map<String, String> nodeResults = new java.util.HashMap<>();

    private AgentOrchestrator orchestrator;

    private WorkflowTaskComponent workflowTaskComponent;

    private ChatMessageComponent chatMessageComponent;

    private TokenUsageDao tokenUsageDao;

    private AgentDefProvider agentDefProvider;

    private AtomicBoolean cancelFlag;

    private WorkflowTaskExecuteComponent workflowTaskExecuteComponent;

    private WorkflowConfirmManager workflowConfirmManager;

    public NodeContext getNodeContext(String nodeId) {
        return nodes.get(nodeId);
    }


}

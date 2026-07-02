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

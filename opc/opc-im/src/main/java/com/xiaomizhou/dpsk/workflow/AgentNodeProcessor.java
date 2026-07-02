package com.xiaomizhou.dpsk.workflow;

import com.xiaomizhou.dpsk.agent.AgentBuildSpec;
import com.xiaomizhou.dpsk.agent.AgentOrchestrator;
import com.xiaomizhou.dpsk.agent.PipelineResult;
import com.xiaomizhou.dpsk.constant.ConversationType;
import com.xiaomizhou.dpsk.db.WorkflowTaskComponent;
import com.xiaomizhou.dpsk.db.WorkflowTaskExecuteComponent;
import com.xiaomizhou.dpsk.db.chat.ImAgentCallback;
import com.xiaomizhou.dpsk.db.dto.WorkflowTaskDto;
import com.xiaomizhou.dpsk.db.model.WorkflowNodeLogDO;
import com.xiaomizhou.dpsk.db.model.WorkflowTaskDO;
import com.xiaomizhou.dpsk.utils.SequenceUtils;
import com.xiaomizhou.dpsk.workflow.xyflow.NodeStep;
import com.yomahub.liteflow.core.NodeComponent;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AgentNodeProcessor extends NodeComponent {
    @Override
    public void process() throws Exception {
        log.info("node executed! nodeId:{}", getNodeId());

        WorkflowContext context = this.getContextBean(WorkflowContext.class);
        NodeContext node = context.getNodeContext(getNodeId());

        // will not happen,just for safety
        if (!NodeStep.NODE_TYPE_COMMON_AGENT.left.equalsIgnoreCase(node.getNodeType())) {
            log.error("start node execute error!nodeId:{}", getNodeId());
            return;
        }

        String taskCode = context.getTaskId();
        String nodeId = node.getNodeId();

        WorkflowTaskExecuteComponent component = context.getWorkflowTaskExecuteComponent();
        WorkflowNodeLogDO nodelog = component.getOne(taskCode, nodeId);
        if (nodelog != null && nodelog.skip()) {
            log.info("start node execute skip!:{}", nodelog.getId());
            return;
        }

        WorkflowTaskComponent workflowTaskComponent = context.getWorkflowTaskComponent();

        WorkflowTaskDto task = workflowTaskComponent.getByCode(taskCode);

        if (WorkflowTaskDO.STATUS_CANCELLED == task.getStatus()) {
            log.info("任务已经取消!");
            return;
        }

        // 开始事件
        Long id = component.start(NodeContext.builder()
                .nodeType(node.getNodeType())
                .nodeId(nodeId)
                .nodeLabel(node.getNodeLabel())
                .build(), taskCode, node.getAgentCode(), task.getContextData());


        ImAgentCallback callback = new ImAgentCallback(context.getUserId(),
                context.getConversationCode(),
                ConversationType.WORKFLOW.name(),
                context.getTargetId(),
                taskCode,
                context.getChatMessageComponent(),
                context.getTokenUsageDao(),
                context.getAgentDefProvider());

        callback.setStreamCode(SequenceUtils.generator().next("STM"));
        callback.setCancelFlag(context.getCancelFlag());
        callback.setSenderInfo(node.getAgentCode());

        AgentOrchestrator orchestrator = context.getOrchestrator();

        // 组装 AgentBuildSpec
        AgentBuildSpec spec = AgentBuildSpec.builder()
                .mode(AgentBuildSpec.MODE_WORKFLOW)
                .userCode(context.getUserId())
                .targetAgentCode(node.getAgentCode())
                .userContent(task.getContextData())
                .conversationCode(context.getConversationCode())
                .mcpCodes(node.getMcpCodes())
                .skillPaths(node.getSkillPaths())
                .taskCode(taskCode)
                .prompt(node.getPrompt())
                .build();

        PipelineResult result = orchestrator.execute(spec, callback);

        if (result.isSuccess()) {
            component.end(id, WorkflowNodeLogDO.STATUS_SUCCESS, null, "", result.getOutputText(), result.getOutputText());
        } else {
            // 失败继续用原始的contextData，重试的时候会用上一次的contextData
            component.end(id, WorkflowNodeLogDO.STATUS_FAILED, WorkflowTaskDO.STATUS_FAILED, result.getOutputText(), null, null);
        }


        log.info("agent node execute!nodeId:{},result:{}", getNodeId(), result);
    }
}

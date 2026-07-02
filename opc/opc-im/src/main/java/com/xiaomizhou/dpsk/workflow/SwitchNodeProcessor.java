package com.xiaomizhou.dpsk.workflow;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
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
import com.yomahub.liteflow.core.NodeSwitchComponent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;

import java.util.List;

@Slf4j
public class SwitchNodeProcessor extends NodeSwitchComponent {

    @Override
    public String processSwitch() throws Exception {
        log.info("switch node execute!nodeId:{}", getNodeId());

        WorkflowContext context = getContextBean(WorkflowContext.class);
        NodeContext node = context.getNodeContext(getNodeId());

        List<NodeContext.NodeCondition> conditions = node.getChooseNodes();

        if (CollectionUtils.isEmpty(conditions)) {
            throw new RuntimeException("Switch node conditions can not be null.");
        }

        String taskCode = context.getTaskId();
        String nodeId = node.getNodeId();

        WorkflowTaskExecuteComponent component = context.getWorkflowTaskExecuteComponent();
        WorkflowNodeLogDO nodelog = component.getOne(taskCode, nodeId);
        if (nodelog != null && nodelog.skip()) {

            // 输出下一个节点的id
            return nodelog.getOutputData();
        }


        WorkflowTaskComponent workflowTaskComponent = context.getWorkflowTaskComponent();

        WorkflowTaskDto task = workflowTaskComponent.getByCode(taskCode);
        if (WorkflowTaskDO.STATUS_CANCELLED == task.getStatus()) {
            log.info("任务已经取消!");
            throw new RuntimeException("任务已经取消");
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
                .userContent(toChoosePrompt(conditions, task.getContextData()))
                .conversationCode(context.getConversationCode())
                .mcpCodes(node.getMcpCodes())
                .skillPaths(node.getSkillPaths())
                .taskCode(taskCode)
                .prompt(node.getPrompt())
                .build();

        PipelineResult result = orchestrator.execute(spec, callback);

        if (!result.isSuccess()) {
            // 失败继续用原始的contextData，重试的时候会用上一次的contextData
            component.end(id, WorkflowNodeLogDO.STATUS_FAILED, WorkflowTaskDO.STATUS_FAILED, result.getOutputText(), null, null);
            throw new RuntimeException("任务执行失败");
        }

        String next = "id_%s".formatted(result.getOutputText());
        component.end(id, WorkflowNodeLogDO.STATUS_SUCCESS, null, "", task.getContextData(), next);
        return next;
    }


    private String toChoosePrompt(List<NodeContext.NodeCondition> conditions, String taskContext) {

        List<String> infos = Lists.newArrayList();

        String template = "nodeId:%s, condition:%s;";

        for (NodeContext.NodeCondition condition : conditions) {
            String nodeId = condition.getNextNodeId();
            String con = condition.getCondition();
            infos.add(template.formatted(nodeId, con));
        }

        return """
                你没有任何背景知识，你需要做的是把用户输入根据条件选择下一个节点。注意：只能返回一个节点id，而且不需要返回任何额外的描述。
                用户输入：%s
                节点和条件：
                %s
                """.formatted(taskContext, Joiner.on(",").join(infos));
    }

}

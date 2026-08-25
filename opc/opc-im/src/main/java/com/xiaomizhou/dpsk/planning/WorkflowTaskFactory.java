package com.xiaomizhou.dpsk.planning;

import com.xiaomizhou.dpsk.db.WorkflowTaskComponent;
import com.xiaomizhou.dpsk.db.dto.WorkflowTaskDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 群聊自主规划专用任务工厂：按规划结果落库一条无模板的工作流任务。
 */
@Component
@RequiredArgsConstructor
public class WorkflowTaskFactory {

    private final WorkflowTaskComponent workflowTaskComponent;

    /**
     * 依据规划图新建并落库一条工作流任务（source=AGENT，status=PENDING）。
     *
     * @param ownerCode      群编码（owner）
     * @param conversationCode 会话编码
     * @param agentCode      首倡 Agent 编码（作任务 agentCode 兜底）
     * @param name           任务名
     * @param workflowJson   规划图 JSON（XyFlow 序列化）
     * @param contextData    输入上下文
     * @return 落库后的任务（含 code）
     */
    public WorkflowTaskDto addByPlan(String ownerCode, String conversationCode, String agentCode,
                                     String name, String workflowJson, String contextData) {
        WorkflowTaskDto task = new WorkflowTaskDto();
        task.setOwnerCode(ownerCode);
        task.setConversationCode(conversationCode);
        task.setAgentCode(agentCode);
        task.setName(name);
        task.setContextData(contextData);
        return workflowTaskComponent.addByPlan(task, workflowJson);
    }
}

package com.xiaomizhou.dpsk.workflow.node;

import com.xiaomizhou.dpsk.db.dto.WorkflowTaskDto;
import com.xiaomizhou.dpsk.db.model.WorkflowNodeLogDO;
import com.xiaomizhou.dpsk.db.model.WorkflowTaskDO;
import com.xiaomizhou.dpsk.workflow.NodeContext;
import com.xiaomizhou.dpsk.workflow.WorkflowContext;
import com.xiaomizhou.dpsk.workflow.api.NodeExecutionContext;
import com.xiaomizhou.dpsk.workflow.api.NodeExecutionResult;
import com.xiaomizhou.dpsk.workflow.api.NodeExecutor;
import lombok.extern.slf4j.Slf4j;

/**
 * 节点执行器模板基类：统一"类型校验 → 跳过检查 → 任务取消检查 → 业务执行"的公共骨架，
 * 消除各节点之间的重复代码。
 *
 * <p>子类只需实现 {@link #doExecute}，并按需覆盖 {@link #validateType}、{@link #isSkipEnabled}、
 * {@link #onSkip}、{@link #onCancelled} 等扩展点。
 */
@Slf4j
public abstract class AbstractNodeExecutor implements NodeExecutor {

    @Override
    public final NodeExecutionResult execute(NodeExecutionContext ctx) throws Exception {
        WorkflowContext wf = ctx.getContext();
        NodeContext node = ctx.getNode();
        if (node == null) {
            throw new IllegalStateException("节点配置不存在: " + ctx.getNodeId());
        }

        // 1. 节点类型安全校验（仅对明确类型的节点生效，防御性检查）
        validateType(node);

        // 2. 跳过检查：节点日志已标记跳过时不再重复执行
        if (isSkipEnabled()) {
            WorkflowNodeLogDO nodeLog = wf.getWorkflowTaskExecuteComponent().getOne(wf.getTaskId(), node.getNodeId());
            if (nodeLog != null && nodeLog.skip()) {
                return onSkip(wf, node, nodeLog);
            }
        }

        // 3. 任务取消检查
        WorkflowTaskDto task = wf.getWorkflowTaskComponent().getByCode(wf.getTaskId());
        if (WorkflowTaskDO.STATUS_CANCELLED == task.getStatus()) {
            return onCancelled(wf, node, task);
        }

        return doExecute(wf, node, task, ctx);
    }

    /** 节点类型校验，子类可覆盖；默认不校验 */
    protected void validateType(NodeContext node) {
    }

    /** 是否启用跳过检查，End 等无需幂等的节点可关闭，默认开启 */
    protected boolean isSkipEnabled() {
        return true;
    }

    /** 节点已执行过（跳过）时的处理，默认跳过执行 */
    protected NodeExecutionResult onSkip(WorkflowContext wf, NodeContext node, WorkflowNodeLogDO nodeLog) {
        log.info("node execute skip! nodeId:{}", node.getNodeId());
        return NodeExecutionResult.ok();
    }

    /** 任务已取消时的处理，默认静默结束；需要中断流程的节点（如 switch）可覆盖为抛异常 */
    protected NodeExecutionResult onCancelled(WorkflowContext wf, NodeContext node, WorkflowTaskDto task) {
        log.info("任务已经取消! nodeId:{}", node.getNodeId());
        return NodeExecutionResult.ok();
    }

    /** 子类业务逻辑 */
    protected abstract NodeExecutionResult doExecute(WorkflowContext wf, NodeContext node, WorkflowTaskDto task, NodeExecutionContext ctx) throws Exception;

    // ===================== 统一的日志 / 状态工具 =====================

    /** 记录节点开始事件，返回节点日志 id */
    protected final Long startLog(WorkflowContext wf, NodeContext node, String agentCode, String inputData) {
        return wf.getWorkflowTaskExecuteComponent().start(
                NodeContext.builder()
                        .nodeType(node.getNodeType())
                        .nodeId(node.getNodeId())
                        .nodeLabel(node.getNodeLabel())
                        .build(),
                wf.getTaskId(), agentCode, inputData);
    }

    /** 记录节点成功结束（不修改任务状态） */
    protected final void endSuccess(WorkflowContext wf, Long logId, String taskOutput, String nodeOutput) {
        wf.getWorkflowTaskExecuteComponent().end(logId, WorkflowNodeLogDO.STATUS_SUCCESS, null, "", taskOutput, nodeOutput);
    }

    /** 记录节点失败结束（任务标记为失败） */
    protected final void endFailed(WorkflowContext wf, Long logId, String error) {
        wf.getWorkflowTaskExecuteComponent().end(logId, WorkflowNodeLogDO.STATUS_FAILED, WorkflowTaskDO.STATUS_FAILED, error, null, null);
    }

    /** 自定义结束（节点状态 / 任务状态 / 输出均由调用方指定） */
    protected final void endTask(WorkflowContext wf, Long logId, Integer nodeStatus, Integer taskStatus,
                                 String error, String taskOutput, String nodeOutput) {
        wf.getWorkflowTaskExecuteComponent().end(logId, nodeStatus, taskStatus, error, taskOutput, nodeOutput);
    }
}

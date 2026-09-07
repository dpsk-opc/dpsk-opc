package com.xiaomizhou.dpsk.workflow.liteflow;

import com.xiaomizhou.dpsk.workflow.WorkflowContext;
import com.xiaomizhou.dpsk.workflow.xyflow.NodeStep;
import com.xiaomizhou.dpsk.workflow.xyflow.XyFlow;
import com.yomahub.liteflow.builder.LiteFlowNodeBuilder;
import com.yomahub.liteflow.builder.el.LiteFlowChainELBuilder;
import com.yomahub.liteflow.core.FlowExecutor;
import com.yomahub.liteflow.flow.LiteflowResponse;
import com.yomahub.liteflow.property.LiteflowConfig;
import lombok.extern.slf4j.Slf4j;

/**
 * LiteFlow 工作流引擎：负责把 {@link XyFlow} 图翻译为 LiteFlow 链并执行。
 *
 * <p>仅此层依赖 LiteFlow API；节点业务逻辑全部在引擎无关的
 * {@link com.xiaomizhou.dpsk.workflow.api.NodeExecutor} 中，
 * 未来接入 LangGraph4j 时，只需参照本类实现 {@code langgraph} 子包对应的引擎即可。
 */
@Slf4j
public class LiteFlowWorkflowEngine {

    /**
     * 构建并执行工作流。
     *
     * @param xyFlow  前端画布模型
     * @param context 由 {@code XyFlowContextBuilder} 构建的任务上下文
     */
    public void execute(XyFlow xyFlow, WorkflowContext context) {
        // 1. 注册节点：分支节点 → switch 适配器，其余 → 通用适配器
        for (NodeStep step : xyFlow.getSteps()) {
            if (xyFlow.isSwitchNode(step.getId())) {
                LiteFlowNodeBuilder.createSwitchNode()
                        .setId(step.getId())
                        .setName(step.getId())
                        .setClazz(LiteFlowSwitchNodeAdapter.class)
                        .build();
            } else {
                LiteFlowNodeBuilder.createCommonNode()
                        .setId(step.getId())
                        .setName(step.getId())
                        .setClazz(LiteFlowCommonNodeAdapter.class)
                        .build();
            }
        }

        // 2. 生成 EL 并构建链
        String el = XyFlowToLiteFlowUtils.toEl(xyFlow);
        log.info("task el. task:{},el:{}", context.getTaskId(), el);
        LiteFlowChainELBuilder.createChain().setChainId(context.getTaskId()).setEL(el).build();

        // 3. 执行
        LiteflowConfig config = new LiteflowConfig();
        config.setChainCacheEnabled(false);
        config.setSupportMultipleType(false);
        config.setEnableMonitorFile(true);
        config.setEnableLog(true);

        FlowExecutor executor = new FlowExecutor(config);
        LiteflowResponse response = executor.execute2Resp(context.getTaskId(), "上下文参数", context);
        log.info("task response. task:{},response:{}", context.getTaskId(), response);
    }
}

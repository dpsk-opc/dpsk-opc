package com.xiaomizhou.dpsk.agent;

/**
 * Agent 编排的产出物。每个 Builder 构建一个 Pipeline。
 * Pipeline 不缓存，每次用户消息都重新构建。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/3
 */
public interface AgentPipeline {

    /**
     * 执行流水线。
     *
     * @param callback 回调接口，用于发射语义事件
     * @return 执行结果（阻塞直到完成）
     */
    PipelineResult execute(AgentCallback callback);
}

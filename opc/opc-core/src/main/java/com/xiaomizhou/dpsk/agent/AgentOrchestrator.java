package com.xiaomizhou.dpsk.agent;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent 编排器，系统唯一入口。
 * 根据 AgentBuildSpec.mode 分发给对应的 Builder。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/3
 */
@Slf4j
public class AgentOrchestrator {

    private final Map<String, AgentBuilder> builders = new HashMap<>();

    /**
     * 注册一个 Builder。
     *
     * @param builder Builder 实例
     */
    public void registerBuilder(AgentBuilder builder) {
        String mode = builder.supportedMode();
        builders.put(mode, builder);
        log.info("Registered AgentBuilder for mode: {}", mode);
    }

    /**
     * 执行编排。
     *
     * @param spec     构建规范
     * @param callback 回调接口
     * @return 执行结果
     */
    public PipelineResult execute(AgentBuildSpec spec, AgentCallback callback) {
        String mode = spec.getMode();
        AgentBuilder builder = builders.get(mode);
        if (builder == null) {
            throw new IllegalArgumentException("No builder registered for mode: " + mode);
        }
        log.info("Orchestrator dispatching mode={}, conversation={}, user={}",
                mode, spec.getConversationCode(), spec.getUserCode());
        AgentPipeline pipeline = builder.build(spec);
        return pipeline.execute(callback);
    }
}

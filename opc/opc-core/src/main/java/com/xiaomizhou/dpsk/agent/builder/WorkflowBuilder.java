package com.xiaomizhou.dpsk.agent.builder;

import com.xiaomizhou.dpsk.agent.*;
import com.xiaomizhou.dpsk.agent.event.AgentEvent;
import lombok.extern.slf4j.Slf4j;

/**
 * Workflow 模式 Builder（预留接口，暂不实现）。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/3
 */
@Slf4j
public class WorkflowBuilder implements AgentBuilder {

    @Override
    public String supportedMode() {
        return AgentBuildSpec.MODE_WORKFLOW;
    }

    @Override
    public AgentPipeline build(AgentBuildSpec spec) {
        return callback -> {
            callback.onEvent(AgentEvent.error("workflow", "Workflow mode is not yet implemented"));
            callback.onComplete();
            return PipelineResult.builder()
                    .success(false)
                    .outputText("Workflow mode is not yet implemented")
                    .build();
        };
    }
}

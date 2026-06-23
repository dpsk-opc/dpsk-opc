package com.xiaomizhou.dpsk.agent;

/**
 * Agent 构建器接口。
 * 每种模式（single/group/workflow）对应一个实现。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/3
 */
public interface AgentBuilder {

    /** 支持的运行模式 */
    String supportedMode();

    /** 根据构建规范产出 AgentPipeline */
    AgentPipeline build(AgentBuildSpec spec);
}

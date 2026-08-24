package com.xiaomizhou.dpsk.workflow.api;

import com.xiaomizhou.dpsk.workflow.node.NodeExecutorRegistry;

/**
 * 节点执行器：工作流节点的引擎无关业务接口。
 *
 * <p>LiteFlow / LangGraph4j 等引擎通过各自的适配器把引擎生命周期翻译为对该接口的调用，
 * 因此节点业务逻辑不依赖任何具体工作流引擎。
 *
 * <p>实现约定：
 * <ul>
 *   <li>普通节点：{@link #execute} 返回 {@link NodeExecutionResult#ok()} 即可；</li>
 *   <li>分支节点（如 switch）：通过返回结果的 {@link NodeExecutionResult#getRouteId()} 表达下一跳；</li>
 *   <li>新增节点类型：实现本接口并注册到 {@link NodeExecutorRegistry}。</li>
 * </ul>
 */
public interface NodeExecutor {

    /**
     * 执行节点。
     *
     * @param context 节点执行上下文
     * @return 执行结果，路由节点通过 routeId 表达下一跳
     * @throws Exception 节点业务中的受检异常（如等待用户确认被中断），由引擎适配层统一处理
     */
    NodeExecutionResult execute(NodeExecutionContext context) throws Exception;
}

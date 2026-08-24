/**
 * LangGraph4j 适配层（预留）。
 *
 * <p>设计约定：LangGraph4j 引擎只需把图的节点 / 条件边翻译为对
 * {@link com.xiaomizhou.dpsk.workflow.api.NodeExecutor} 的调用：
 * <ul>
 *   <li>节点执行 → {@code NodeExecutor#execute(NodeExecutionContext)}；</li>
 *   <li>条件路由 → 从执行结果的 {@code routeId} 决定下一跳；</li>
 *   <li>上下文构建复用 {@code XyFlowContextBuilder}（引擎无关）。</li>
 * </ul>
 * 待引入 langgraph4j 依赖后，在本包实现对应的节点适配器与图构建引擎（参照 liteflow 子包）。
 */
package com.xiaomizhou.dpsk.workflow.langgraph;

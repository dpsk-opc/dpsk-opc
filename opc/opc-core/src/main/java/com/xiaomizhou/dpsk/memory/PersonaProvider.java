package com.xiaomizhou.dpsk.memory;

/**
 * Agent 人设（persona）提供者接口。
 * <p>
 * opc-core 仅定义接口，具体实现由 opc-im 基于 AgentComponent 提供。
 * DatabaseChatMemoryStore 在从 DB 加载 L0 历史消息后，会将 persona 作为 SystemMessage 前置插入。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/30
 */
@FunctionalInterface
public interface PersonaProvider {

    /**
     * 根据 Agent 编码获取其 persona 文本。
     *
     * @param agentCode Agent 编码
     * @return persona 文本，若 Agent 不存在返回 null
     */
    String getPersona(String agentCode);
}

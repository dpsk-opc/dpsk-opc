package com.xiaomizhou.dpsk.agent.event;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 语义事件载体（Record，不可变）。
 * Agent 执行过程中通过 AgentCallback 发射此类事件。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/3
 */
public record AgentEvent(
        AgentEventType type,
        String agentCode,
        String text,
        String toolName,
        String toolInput,
        String toolOutput,
        Map<String, Object> meta
) {

    /** 创建 THINKING 事件 */
    public static AgentEvent thinking(String agentCode, String text) {
        return new AgentEvent(AgentEventType.THINKING, agentCode, text, null, null, null, Collections.emptyMap());
    }

    /** 创建 STREAM_CHUNK 事件 */
    public static AgentEvent streamChunk(String agentCode, String delta) {
        return new AgentEvent(AgentEventType.STREAM_CHUNK, agentCode, delta, null, null, null, Collections.emptyMap());
    }

    /** 创建 TOOL_CALL 事件 */
    public static AgentEvent toolCall(String agentCode, String name, String input) {
        return new AgentEvent(AgentEventType.TOOL_CALL, agentCode, null, name, input, null, Collections.emptyMap());
    }

    /** 创建 TOOL_RESULT 事件 */
    public static AgentEvent toolResult(String agentCode, String name, String output) {
        return new AgentEvent(AgentEventType.TOOL_RESULT, agentCode, null, name, null, output, Collections.emptyMap());
    }

    /** 创建 DONE 事件 */
    public static AgentEvent done(String agentCode, Map<String, Object> meta) {
        return new AgentEvent(AgentEventType.DONE, agentCode, null, null, null, null,
                meta != null ? new HashMap<>(meta) : Collections.emptyMap());
    }

    /** 创建 ERROR 事件 */
    public static AgentEvent error(String agentCode, String message) {
        return new AgentEvent(AgentEventType.ERROR, agentCode, message, null, null, null, Collections.emptyMap());
    }

    public static AgentEvent msgRead(String agentCode, String messageCode) {
        return new AgentEvent(AgentEventType.MSG_READ, agentCode, messageCode, null, null, null, null);
    }
}

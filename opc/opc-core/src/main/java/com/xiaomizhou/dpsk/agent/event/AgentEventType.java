package com.xiaomizhou.dpsk.agent.event;

/**
 * Agent 执行过程中产生的语义事件类型。
 * opc-core 发射这些事件，opc-im 负责将其翻译为前端 WsMessage。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/3
 */
public enum AgentEventType {

    /** Agent 开始思考（深色推理） */
    THINKING,

    /** LLM 开始调用工具 */
    TOOL_CALL,

    /** LLM 调用工具返回结果 */
    TOOL_CALL_CHUNK,

    /** 工具调用返回结果 */
    TOOL_RESULT,

    /** 流式输出文本增量 */
    STREAM_CHUNK,

    /** 流式输出文本增量结束 */
    STREAM_CHUNK_END,

    /**
     * 普通消息
     */
    MESSAGE,

    /**
     * 消息开始
     */
    MESSAGE_CHUNK,

    /**
     * 消息结束
     */
    MESSAGE_CHUNK_END,

    /** 执行完成（含完整结果） */
    DONE,

    /** 执行异常 */
    ERROR,

    /**
     * 消息已读
     */
    MSG_READ
}

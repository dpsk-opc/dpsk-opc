package com.xiaomizhou.dpsk.core.ws;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/22 14:20
 * @description
 */
public interface WsMsgType {

    // 普通消息（非流式）
    String MESSAGE = "message_start";

    String MESSAGE_CHUNK = "message_chunk";

    String MESSAGE_CHUNK_END = "message_end";

    String MESSAGE_DONE = "done";

    // 流式消息开始
    String STREAM_START = "thinking_start";
    // 流式消息块
    String STREAM_CHUNK = "thinking_chunk";
    // 流式消息结束
    String STREAM_END = "thinking_end";

    // 工具调用
    String TOOL_CALL = "tool_call";

    // 工具调用结果
    String TOOL_RESULT = "tool_result";


    // 心跳 ping/pong
    String PING = "ping";

    String PONG = "pong";

    // 已读回执
    String READ_RECEIPT = "read_receipt";

    // 错误（服务端主动推送）
    String ERROR = "error";

    // MCP 工具调用（后端 -> 前端 Electron）
    String MCP_CALL = "mcp_call";
    // MCP 工具调用结果（前端 Electron -> 后端）
    String MCP_CALL_RESULT = "mcp_call_result";

    // 会话取消通知
    String CANCEL = "cancel";

    // 待办到期提醒
    String TODO_REMIND = "todo_remind";

    // 任务确认
    String TASK_CONFIRM = "task_confirm";

    // Agent 向用户提问（ask_user 工具）
    String TOOL_ASK = "tool_ask";

    // 提问超时/取消（后端 -> 前端）
    String TOOL_ASK_TIMEOUT = "tool_ask_timeout";
}

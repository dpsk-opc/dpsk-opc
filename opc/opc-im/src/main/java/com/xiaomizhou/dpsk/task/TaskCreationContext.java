package com.xiaomizhou.dpsk.task;

/**
 * 任务创建上下文（ThreadLocal），用于透传 Agent 调用时的环境参数。
 * <p>
 * 在 AI Pipeline 执行前由 {@link com.xiaomizhou.dpsk.db.chat.AgentBridge} 或
 * {@link com.xiaomizhou.dpsk.task.consumer.AgentTaskConsumer} 设置，
 * 工具（如 CreateScheduledTaskTool）内部直接从 ThreadLocal 读取，
 * 避免将 userId/agentCode/conversationCode 暴露给 LLM。
 * <p>
 * <b>注意：每次调用后必须 clear()，防止内存泄漏和线程池复用导致的串值。</b>
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/4
 */
public class TaskCreationContext {

    private static final ThreadLocal<String> USER_ID_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> AGENT_CODE_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> CONVERSATION_CODE_HOLDER = new ThreadLocal<>();

    private TaskCreationContext() {
    }

    /**
     * 设置当前上下文的全部参数。
     */
    public static void set(String userId, String agentCode, String conversationCode) {
        USER_ID_HOLDER.set(userId);
        AGENT_CODE_HOLDER.set(agentCode);
        CONVERSATION_CODE_HOLDER.set(conversationCode);
    }

    public static String getUserId() {
        return USER_ID_HOLDER.get();
    }

    public static String getAgentCode() {
        return AGENT_CODE_HOLDER.get();
    }

    public static String getConversationCode() {
        return CONVERSATION_CODE_HOLDER.get();
    }

    /**
     * 清除当前上下文（务必在 finally 中调用）。
     */
    public static void clear() {
        USER_ID_HOLDER.remove();
        AGENT_CODE_HOLDER.remove();
        CONVERSATION_CODE_HOLDER.remove();
    }
}

package com.xiaomizhou.dpsk.memory.config;

import lombok.Getter;

import java.util.Objects;

/**
 * 记忆隔离键，用于区分不同会话、不同 Agent 的记忆空间。
 * <p>
 * memoryId 格式：
 * <ul>
 *   <li>单聊：conv:{conversation_code}:agent:{owner_code}</li>
 *   <li>群聊：conv:{conversation_code}:group:{group_code}:agent:{owner_code}</li>
 *   <li>单聊/群聊（携带锚定用户消息）：...:um:{user_message_code}</li>
 * </ul>
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/29
 */
public class MemoryKey {

    /** 会话编码 */
    @Getter
    private final String conversationCode;

    /** Agent 编码 */
    @Getter
    private final String ownerCode;

    /** 群组编码（群聊场景，单聊为 null） */
    @Getter
    private final String groupCode;

    /** 任务ID（任务聊场景，单聊为 null） */
    @Getter
    private final String taskCode;

    /**
     * 本次触发执行的用户消息编码（锚定用户原始需求，可为空）。
     * 由 AgentComponentFactory 在构造 memoryId 时注入，仅用于 L0 窗口兜底时精确取回真实用户消息。
     */
    @Getter
    private final String userMessageCode;

    public MemoryKey(String conversationCode, String ownerCode) {
        this(conversationCode, ownerCode, null, null, null);
    }

    public MemoryKey(String conversationCode, String ownerCode, String groupCode, String taskCode) {
        this(conversationCode, ownerCode, groupCode, taskCode, null);
    }

    public MemoryKey(String conversationCode, String ownerCode, String groupCode, String taskCode, String userMessageCode) {
        this.conversationCode = conversationCode;
        this.ownerCode = ownerCode;
        this.groupCode = groupCode;
        this.taskCode = taskCode;
        this.userMessageCode = userMessageCode;
    }

    /** 是否为群聊记忆 */
    public boolean isGroupChat() {
        return groupCode != null;
    }

    public boolean isTaskChat() {
        return taskCode != null;
    }

    /**
     * 构建 memoryId 字符串。
     */
    public String toMemoryId() {
        return MemoryConfig.appendUserMessageCode(toStableMemoryId(), userMessageCode);
    }

    /**
     * 返回不含 userMessageCode 的稳定 memoryId（用于快照、移出检测等需保持空间稳定的场景）。
     * <p>
     * 说明：buildWorkflowMemoryId(conversationCode, ownerCode, workflowTaskId) 的形参命名与
     * 实际格式化位置相反（task 位放第 2 参、agent 位放第 3 参），此处显式按「task 位=taskCode、
     * agent 位=ownerCode」的语义传参，保证 task 分支生成的 key 自洽一致。
     */
    public String toStableMemoryId() {
        if (isGroupChat()) {
            return MemoryConfig.buildGroupMemoryId(conversationCode, groupCode, ownerCode);
        }
        if (isTaskChat()) {
            return MemoryConfig.buildWorkflowMemoryId(conversationCode, taskCode, ownerCode);
        }
        return MemoryConfig.buildMemoryId(conversationCode, ownerCode);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MemoryKey)) return false;
        MemoryKey that = (MemoryKey) o;
        return Objects.equals(conversationCode, that.conversationCode)
                && Objects.equals(ownerCode, that.ownerCode)
                && Objects.equals(groupCode, that.groupCode)
                && Objects.equals(taskCode, that.taskCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(conversationCode, ownerCode, groupCode, taskCode);
    }

    @Override
    public String toString() {
        return toMemoryId();
    }
}

package com.xiaomizhou.dpsk.memory.config;

import java.util.Objects;

/**
 * 记忆隔离键，用于区分不同会话、不同 Agent 的记忆空间。
 * <p>
 * memoryId 格式：
 * <ul>
 *   <li>单聊：conv:{conversation_code}:agent:{owner_code}</li>
 *   <li>群聊：conv:{conversation_code}:group:{group_code}:agent:{owner_code}</li>
 * </ul>
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/29
 */
public class MemoryKey {

    /** 会话编码 */
    private final String conversationCode;

    /** Agent 编码 */
    private final String ownerCode;

    /** 群组编码（群聊场景，单聊为 null） */
    private final String groupCode;

    public MemoryKey(String conversationCode, String ownerCode) {
        this(conversationCode, ownerCode, null);
    }

    public MemoryKey(String conversationCode, String ownerCode, String groupCode) {
        this.conversationCode = conversationCode;
        this.ownerCode = ownerCode;
        this.groupCode = groupCode;
    }

    public String getConversationCode() {
        return conversationCode;
    }

    public String getOwnerCode() {
        return ownerCode;
    }

    /** 群组编码，单聊返回 null */
    public String getGroupCode() {
        return groupCode;
    }

    /** 是否为群聊记忆 */
    public boolean isGroupChat() {
        return groupCode != null;
    }

    /**
     * 构建 memoryId 字符串。
     */
    public String toMemoryId() {
        if (isGroupChat()) {
            return MemoryConfig.buildGroupMemoryId(conversationCode, groupCode, ownerCode);
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
                && Objects.equals(groupCode, that.groupCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(conversationCode, ownerCode, groupCode);
    }

    @Override
    public String toString() {
        return toMemoryId();
    }
}

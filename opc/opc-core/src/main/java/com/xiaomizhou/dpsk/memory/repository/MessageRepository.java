package com.xiaomizhou.dpsk.memory.repository;

import com.xiaomizhou.dpsk.memory.config.MemoryKey;
import dev.langchain4j.data.message.ChatMessage;

import java.util.List;

/**
 * 消息持久化仓储接口。
 * <p>
 * opc-core 仅定义接口，具体实现由 opc-im 基于 MyBatis-Plus 提供。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/29
 */
public interface MessageRepository {

    /**
     * 获取指定会话中某个 Agent 的最近 N 条消息（用于 L0 工作记忆）。
     * <p>
     * 过滤掉 status = 'IGNORE' 的消息，不计入上下文。
     *
     * @param conversationCode 会话编码
     * @param ownerCode        Agent 编码
     * @param limit            最大消息条数
     * @return ChatMessage 列表（时间正序）
     */
    List<ChatMessage> findTopByConversationAndAgent(String conversationCode, String ownerCode, int limit);


    /**
     *
     * @param conversationCode
     * @param ownerCode
     * @param taskId
     * @param limit
     * @return
     */
    List<ChatMessage> findTopTaskMessagesForAgent(String conversationCode, String ownerCode, String taskId, int limit);

    /**
     * 获取群聊中最近 N 条消息（用于 L0 工作记忆）。
     * <p>
     * 群聊消息按 receiver_code = groupCode 且 conversation_type = 'GROUP' 查询。
     * 发送者为 agentCode 的消息标记为 AiMessage，其余标记为 UserMessage。
     * 过滤掉 status = 'IGNORE' 的消息，不计入上下文。
     *
     * @param groupCode 群组编码
     * @param agentCode Agent 编码（用于区分自己发出的消息）
     * @param limit     最大消息条数
     * @return ChatMessage 列表（时间正序）
     */
    List<ChatMessage> findTopGroupMessages(String groupCode, String agentCode, int limit);

    /**
     * 根据消息 code 查询单条消息。
     *
     * @param messageCode 消息编码
     * @return ChatMessage，未找到返回 null
     */
    ChatMessage findByCode(String messageCode);

    /**
     * 获取某条消息的前后上下文（用于 @引用）。
     * 过滤掉 status = 'IGNORE' 的消息，不计入上下文。
     *
     * @param messageCode 目标消息编码
     * @param contextSize 前后各取条数
     * @return 上下文消息列表（时间正序）
     */
    List<ChatMessage> findContext(String messageCode, int contextSize);

    /**
     * 批量保存新消息。
     *
     * @param messages 待保存的消息列表
     * @param memoryKey 工作记忆ID
     */
    void saveMessages(MemoryKey memoryKey, List<ChatMessage> messages);

    /**
     * 获取上次存储的快照消息编码列表（用于检测移出窗口的消息）。
     *
     * @param conversationCode 会话编码
     * @param ownerCode        Agent 编码
     * @param limit            最大条数
     * @return 消息编码列表（时间正序）
     */
    List<String> findMessageCodesByConversationAndAgent(String conversationCode, String ownerCode, int limit);
}

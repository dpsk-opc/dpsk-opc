package com.xiaomizhou.dpsk.memory.store;

import com.google.common.collect.Lists;
import com.xiaomizhou.dpsk.memory.assembler.ContextAssembler;
import com.xiaomizhou.dpsk.memory.config.MemoryConfig;
import com.xiaomizhou.dpsk.memory.config.MemoryKey;
import com.xiaomizhou.dpsk.memory.manager.MemoryManager;
import com.xiaomizhou.dpsk.memory.repository.ConversationRepository;
import com.xiaomizhou.dpsk.memory.repository.MessageRepository;
import com.xiaomizhou.dpsk.utils.MemoryUtils;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.*;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 基于数据库的 ChatMemoryStore 实现（L0 工作记忆持久化）。
 * <p>
 * 实现 LangChain4j 的 ChatMemoryStore 接口，将 L0 消息持久化到数据库。
 * 内部维护消息快照，当检测到消息被移出窗口时，触发 MemoryManager 的异步更新流水线。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/29
 */
public class DatabaseChatMemoryStore implements ChatMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(DatabaseChatMemoryStore.class);

    private final MessageRepository messageRepository;
    private final MemoryManager memoryManager;

    private final ContextAssembler.AssembledPrompt assembledPrompt;

    private final ConversationRepository conversationRepository;

    /**
     * 内存快照：memoryId -> 消息指纹列表（用于检测移出窗口的消息）
     */
    private final Map<Object, List<String>> snapshotMap = new ConcurrentHashMap<>();

    /**
     * 构造函数。
     *
     * @param messageRepository 消息仓储（由 opc-im 实现并注入）
     * @param memoryManager     记忆管理器（可为 null，仅 L0 模式）
     */
    public DatabaseChatMemoryStore(MessageRepository messageRepository,
                                   MemoryManager memoryManager,
                                   ContextAssembler.AssembledPrompt assembledPrompt,
                                   ConversationRepository conversationRepository) {
        this.messageRepository = Objects.requireNonNull(messageRepository, "messageRepository must not be null");
        this.memoryManager = memoryManager;
        this.assembledPrompt = Objects.requireNonNull(assembledPrompt, "assembledPrompt can not be null.");
        this.conversationRepository = Objects.requireNonNull(conversationRepository, "conversationRepository can not be null.");
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        MemoryKey key = MemoryConfig.parseMemoryId(memoryId);
        if (key == null) {
            log.warn("Invalid memoryId: {}", memoryId);
            return List.of();
        }

        // 使用 2x 限制获取更多消息，避免工具调用组（THINKING + TOOL）被 LIMIT 截断
        int fetchLimit = MemoryConfig.L0_MAX_MESSAGES + 10;
        List<ChatMessage> dbMessages;
        if (key.isGroupChat()) {
            // 群聊：按 groupCode 查询所有消息，从当前 Agent 视角区分 User/AI
            dbMessages = messageRepository.findTopGroupMessages(
                    key.getGroupCode(), key.getOwnerCode(), fetchLimit);
        } else if(key.isTaskChat()){
            // 专家团：按 taskId 查询所有消息，从当前 Agent 视角区分 User/AI
            dbMessages = messageRepository.findTopTaskMessagesForAgent(key.getConversationCode(),
                    key.getOwnerCode(), key.getTaskCode(), fetchLimit);
        } else {
            // 单聊：按 conversationCode + ownerCode 查询
            dbMessages = messageRepository.findTopByConversationAndAgent(
                    key.getConversationCode(), key.getOwnerCode(), fetchLimit);
        }

        // 不会出现
        if (CollectionUtils.isEmpty(dbMessages)) {
            return List.of();
        }


        List<ChatMessage> result = Lists.newArrayList();

        // 补系统消息
        if (CollectionUtils.isEmpty(dbMessages) || !(dbMessages.get(0) instanceof SystemMessage)) {
            result.add(SystemMessage.from(assembledPrompt.getFullPrompt()));
        }


        // 确保工具调用消息配对完整（THINKING 的 call_id 与 TOOL 的 id 必须成对）
        dbMessages = ensureToolPairing(dbMessages);

        // 裁剪到 L0_MAX_MESSAGES，但不切断 THINKING-TOOL 工具调用组
        if (CollectionUtils.isNotEmpty(result)) {
            int size = MemoryConfig.L0_MAX_MESSAGES - result.size();
            dbMessages = trimKeepLatest(dbMessages, size);
        }

        result.addAll(dbMessages);

        // 最终兜底：确保返回的完整消息列表中至少存在一条 UserMessage。
        // 原因：LangChain4j 在 refreshDynamicProviders 中会执行
        //   UserMessage.findLast(messages).orElseThrow()
        // 当工具调用密集导致 UserMessage 被裁剪（trimKeepLatest）挤出窗口时，
        // 最终列表会没有任何 UserMessage，从而抛出 NoSuchElementException。
        //
        // 关键：补的 UserMessage 必须放在「列表末尾」，而不能放在 SystemMessage 之后（index 1）。
        // 因为 MessageWindowChatMemory.messages() 在拿到 store.getMessages() 的返回值后，
        // 还会再调用 ensureCapacity(messages, maxMessages) 从「头部」逐出最早的非 SystemMessage。
        // 若补在 index 1，会第一个被 ensureCapacity 逐出，等于白补。
        // 补在末尾（最新位置）则不会被 ensureCapacity 从头部逐出，findLast 能稳定命中；
        // 且 messagesToSend() 里 UserMessage.replaceLast() 会把它替换成当前真实用户消息，语义正确。
        if (UserMessage.findLast(result).isEmpty()) {
            log.warn("No UserMessage in final memory window after trimming, padding one back. memoryId: {}", memoryId);
            String conversationCode = key.getConversationCode();
            String lastUserContent = conversationRepository.getLastUserContent(conversationCode);
            if (StringUtils.isNotBlank(lastUserContent)) {
                result.add(UserMessage.from(lastUserContent));
                log.warn("a real lastUserContent: {}", lastUserContent);
            } else {
                // 无用的，避免底层爆异常
                result.add(UserMessage.from("xxxxx"));
                log.warn("a empty lastUserContent: {}", lastUserContent);
            }
        }

        return new ArrayList<>(result);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        MemoryKey key = MemoryConfig.parseMemoryId(memoryId);
        if (key == null) {
            log.warn("Invalid memoryId in updateMessages: {}", memoryId);
            return;
        }

        // 过滤掉 SystemMessage（persona），不参与持久化、指纹计算和窗口淘汰
        List<ChatMessage> filtered = messages.stream()
                .filter(m -> !(m instanceof SystemMessage))
                .collect(Collectors.toList());

        // 计算新快照的消息指纹
        List<String> newFingerprints = computeFingerprints(filtered);

        // 获取旧快照
        List<String> oldFingerprints = snapshotMap.getOrDefault(memoryId, List.of());

        // 持久化到数据库（仅持久化 User/AI 消息）
        messageRepository.saveMessages(key, new ArrayList<>(filtered));

        // 更新内存快照
        snapshotMap.put(memoryId, newFingerprints);

        // 检测移出窗口的消息
        if (memoryManager != null && !oldFingerprints.isEmpty()) {
            List<String> evictedFingerprints = oldFingerprints.stream()
                    .filter(fp -> !newFingerprints.contains(fp))
                    .collect(Collectors.toList());

            if (!evictedFingerprints.isEmpty()) {
                List<ChatMessage> evictedMessages = rebuildEvictedMessages(evictedFingerprints, oldFingerprints, filtered);
                if (!evictedMessages.isEmpty()) {
                    memoryManager.onMessagesEvicted(key, evictedMessages);
                }
            }
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        snapshotMap.remove(memoryId);
        log.debug("deleteMessages called for memoryId: {}", memoryId);
    }

    /**
     * 计算消息指纹列表。
     */
    private List<String> computeFingerprints(List<ChatMessage> messages) {
        return messages.stream()
                .map(this::fingerprint)
                .collect(Collectors.toList());
    }

    /**
     * 为单条消息生成指纹。
     */
    private String fingerprint(ChatMessage message) {
        if (message instanceof AiMessage) {
            return "AI:" + ((AiMessage) message).text();
        } else if (message instanceof UserMessage) {
            return MemoryUtils.toSingleContent((UserMessage) message);
        }
        return message.type().name() + ":" + message.hashCode();
    }

    /**
     * 从快照指纹重建被移出的消息。
     * <p>
     * 简化策略：将移出指纹对应的文本片段包装为 ChatMessage。
     * opc-im 可以实现更精确的重建逻辑。
     */
    private List<ChatMessage> rebuildEvictedMessages(List<String> evictedFingerprints,
                                                     List<String> allOldFingerprints,
                                                     List<ChatMessage> currentMessages) {
        List<ChatMessage> result = new ArrayList<>();
        // 使用一个简易的索引：在旧快照中找位置，然后推算消息
        // 由于被移出的消息不在 currentMessages 中，这里使用指纹解析
        for (String fp : evictedFingerprints) {
            if (fp.startsWith("AI:")) {
                result.add(AiMessage.from(fp.substring(3)));
            } else if (fp.startsWith("USER:")) {
                result.add(UserMessage.from(fp.substring(5)));
            }
        }
        return result;
    }

    /**
     * 确保工具调用消息配对完整，移除不完整的 THINKING-TOOL 组。
     * <p>
     * 当 L0 消息窗口被裁剪时，可能出现：
     * <ul>
     *   <li>TOOL 消息存在，但对应的 THINKING（含 toolExecutionRequest）已被淘汰 → 孤儿 TOOL</li>
     *   <li>THINKING 消息存在但部分 TOOL 结果已被淘汰 → 不完整的工具调用组</li>
     * </ul>
     * <p>
     * 规则：以 THINKING 消息为单位，其所有 toolExecutionRequest.id 必须有对应的 TOOL 消息，
     * 否则整个 THINKING 组（含该 THINKING 和所有关联 TOOL）被移除。
     *
     * @param messages 原始消息列表（时间正序）
     * @return 过滤后的消息列表
     */
    static List<ChatMessage> ensureToolPairing(List<ChatMessage> messages) {
        if (messages.isEmpty()) {
            return messages;
        }

        // 第一步：收集所有 TOOL 消息的 id
        Set<String> toolResultIds = new HashSet<>();
        for (ChatMessage msg : messages) {
            if (msg instanceof ToolExecutionResultMessage toolMsg) {
                toolResultIds.add(toolMsg.id());
            }
        }

        // 第二步：按 THINKING 消息分组判断完整性
        // validRequestIds: 完整配对组内的 call_id（保留）
        // invalidRequestIds: 不完整配对组内的 call_id（移除）
        Set<String> validRequestIds = new HashSet<>();

        for (ChatMessage msg : messages) {
            if (msg instanceof AiMessage aiMsg && aiMsg.hasToolExecutionRequests()) {
                List<ToolExecutionRequest> requests = aiMsg.toolExecutionRequests();
                boolean allMatched = requests.stream()
                        .allMatch(req -> toolResultIds.contains(req.id()));

                if (allMatched) {
                    for (ToolExecutionRequest req : requests) {
                        validRequestIds.add(req.id());
                    }
                } else {
                    log.debug("Dropping THINKING message with incomplete tool results: "
                                    + "expected={}, found={}",
                            requests.stream().map(ToolExecutionRequest::id).collect(Collectors.toList()),
                            requests.stream().filter(r -> toolResultIds.contains(r.id()))
                                    .map(ToolExecutionRequest::id).collect(Collectors.toList()));
                }
            }
        }

        // 第三步：过滤消息列表
        List<ChatMessage> result = new ArrayList<>();
        for (ChatMessage msg : messages) {
            if (msg instanceof ToolExecutionResultMessage toolMsg) {
                // TOOL 消息：只保留有效 call_id 的
                if (validRequestIds.contains(toolMsg.id())) {
                    result.add(msg);
                }
            } else if (msg instanceof AiMessage aiMsg && aiMsg.hasToolExecutionRequests()) {
                // THINKING 消息：所有 requests 都有效才保留
                boolean allValid = aiMsg.toolExecutionRequests().stream()
                        .allMatch(req -> validRequestIds.contains(req.id()));
                if (allValid) {
                    result.add(msg);
                }
            } else {
                // 普通消息（USER / 纯文本 AI）：直接保留
                result.add(msg);
            }
        }
        return result;
    }

    /**
     * 保留最新的 maxCount 条消息，但不切断 THINKING-TOOL 工具调用组。
     * <p>
     * 当裁剪边界恰好落在 THINKING 消息与其 TOOL 结果之间时，
     * 自动向前扩展以包含完整的 THINKING 消息。
     *
     * @param messages 已通过 {@link #ensureToolPairing} 配对检查的消息列表（时间正序）
     * @param maxCount 最大保留条数
     * @return 裁剪后的消息列表
     */
    static List<ChatMessage> trimKeepLatest(List<ChatMessage> messages, int maxCount) {
        if (messages.size() <= maxCount) {
            return new ArrayList<>(messages);
        }

        int start = messages.size() - maxCount;

        // 构建裁剪边界之前的 THINKING 消息中 requestId → 位置 的映射
        Map<String, Integer> requestToThinkingPos = new HashMap<>();
        for (int i = 0; i < start; i++) {
            ChatMessage msg = messages.get(i);
            if (msg instanceof AiMessage ai && ai.hasToolExecutionRequests()) {
                for (ToolExecutionRequest req : ai.toolExecutionRequests()) {
                    requestToThinkingPos.put(req.id(), i);
                }
            }
        }

        // 检查 start 及之后的 TOOL 消息，其父 THINKING 是否在 start 之前
        int actualStart = start;
        for (int i = start; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            if (msg instanceof ToolExecutionResultMessage toolMsg) {
                Integer thinkingPos = requestToThinkingPos.get(toolMsg.id());
                if (thinkingPos != null && thinkingPos < start) {
                    // 该 TOOL 的 THINKING 在裁剪点之前，需要向前扩展以包含完整的 THINKING
                    actualStart = Math.min(actualStart, thinkingPos);
                }
            }
        }

        if (actualStart < start) {
            log.debug("Tool group boundary detected: extended trim window from {} to {}", start, actualStart);
        }

        return new ArrayList<>(messages.subList(actualStart, messages.size()));
    }

    /**
     * 确保裁剪后的消息列表以 UserMessage 开头，避免 LangChain4j 框架因找不到最近的
     * UserMessage 而报错。
     * <p>
     * 场景：消息序列为 {@code [UserMsg, THINKING, TOOL, ..., THINKING, TOOL]}，
     * 当消息总数超过窗口限制时，{@link #trimKeepLatest} 会保留最新的 N 条消息，
     * 导致 UserMessage 被淘汰，列表以 THINKING/TOOL 开头。
     * <p>
     * 修正策略：从列表头部开始，持续移除开头的 THINKING+TOOL 对，
     * 直到列表以 UserMessage（或纯文本 AiMessage）开头为止。
     *
     * @param messages 已裁剪的消息列表（时间正序）
     * @param maxCount 窗口最大消息数
     * @return 修正后的消息列表
     */
    static List<ChatMessage> ensureStartsWithUserMessage(List<ChatMessage> messages, int maxCount) {
        if (messages.isEmpty()) {
            return messages;
        }

        int cursor = 0;

        while (cursor < messages.size()) {
            ChatMessage first = messages.get(cursor);

            // 以 UserMessage 或纯文本 AiMessage（非工具调用）开头 → 符合预期，停止
            if (first instanceof UserMessage) {
                break;
            }
            if (first instanceof AiMessage ai && !ai.hasToolExecutionRequests()) {
                break;
            }

            // 开头是 THINKING 或 TOOL，找到第一组完整的 THINKING+TOOL 对
            Set<String> pendingToolIds = new HashSet<>();
            int groupEnd = -1;

            for (int i = cursor; i < messages.size(); i++) {
                ChatMessage msg = messages.get(i);
                if (msg instanceof AiMessage ai2 && ai2.hasToolExecutionRequests()) {
                    for (ToolExecutionRequest req : ai2.toolExecutionRequests()) {
                        pendingToolIds.add(req.id());
                    }
                } else if (msg instanceof ToolExecutionResultMessage toolMsg) {
                    pendingToolIds.remove(toolMsg.id());
                    if (pendingToolIds.isEmpty()) {
                        groupEnd = i;
                        break;
                    }
                } else {
                    // 遇到 UserMessage 或纯文本 AiMessage，THINKING/TOOL 组不完整
                    break;
                }
            }

            if (groupEnd > cursor && groupEnd < messages.size() - 1) {
                log.debug("Removing leading THINKING-TOOL group [{}-{}] to keep UserMessage in window",
                        cursor, groupEnd);
                cursor = groupEnd + 1;
            } else {
                // 无法安全移除（例如只剩一组 THINKING+TOOL 或组不完整）
                break;
            }
        }

        if (cursor > 0) {
            return new ArrayList<>(messages.subList(cursor, messages.size()));
        }
        return messages;
    }
}

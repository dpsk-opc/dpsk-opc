package com.xiaomizhou.dpsk.memory.config;

/**
 * 上下文记忆系统配置常量。
 * <p>
 * 所有可调参数集中在此处，便于统一管理和调优。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/29
 */
public final class MemoryConfig {

    private MemoryConfig() {
        // 工具类，禁止实例化
    }

    // ======================== L0 工作记忆 ========================

    /** L0 最大 token 数 */
    public static final int L0_MAX_TOKENS = 4000;

    /** L0 最大消息条数（备选限制） */
    public static final int L0_MAX_MESSAGES = 40;

    // ======================== L1 摘要记忆 ========================

    /** 触发摘要的最小移出消息数 */
    public static final int L1_MIN_EVICTED = 6;

    /** 摘要 Prompt 模板 */
    public static final String L1_SUMMARY_PROMPT_TEMPLATE =
            "你是一个对话摘要助手。请以 {agent_name} 的第一人称视角，将旧摘要和新对话合并成简洁的摘要。\n" +
            "只保留关键事件、用户偏好、情感变化、未完成的任务。避免流水账。\n" +
            "- 旧摘要：{previous_summary}\n" +
            "- 新对话：\n{formatted_messages}\n\n" +
            "合并后的摘要：";

    // ======================== L2 语义记忆（纯 RAG） ========================

    /** 每次检索的最多历史消息片段数 */
    public static final int L2_RETRIEVAL_TOPK = 3;

    /** 语义相似度阈值 */
    public static final double L2_RETRIEVAL_MIN_SCORE = 0.75;

    /** 记忆时间衰减系数 λ（每天） */
    public static final double L2_DECAY_LAMBDA = 0.01;

    // ======================== Memory ID 模板 ========================

    /** memoryId 格式：conv:{conversation_code}:agent:{owner_code} */
    public static final String MEMORY_ID_TEMPLATE = "conv:%s:agent:%s";

    /** 群聊 memoryId 格式：conv:{conversation_code}:group:{group_code}:agent:{owner_code} */
    public static final String GROUP_MEMORY_ID_TEMPLATE = "conv:%s:group:%s:agent:%s";


    /** 任务模式 memoryId 格式：conv:{conversation_code}:task:{task_id}:agent:{owner_code} */
    public static final String WORKFLOW_MEMORY_ID_TEMPLATE = "conv:%s:task:%s:agent:%s";

    /** 锚定用户消息 code 段（memoryId 末尾追加段）：:um:{user_message_code} */
    public static final String USER_MSG_CODE_SEGMENT = "um";

    /** 锚定用户消息 code 段模板 */
    public static final String USER_MSG_CODE_TEMPLATE = ":%s:%s";

    /**
     * 向 memoryId 追加锚定用户消息 code 段（code 为空时原样返回）。
     *
     * @param memoryId        基础 memoryId（不含 code 段）
     * @param userMessageCode 用户消息 code（可为空）
     * @return 追加 code 段后的 memoryId
     */
    public static String appendUserMessageCode(String memoryId, String userMessageCode) {
        if (userMessageCode == null || userMessageCode.isBlank() || memoryId == null) {
            return memoryId;
        }
        return memoryId + String.format(USER_MSG_CODE_TEMPLATE, USER_MSG_CODE_SEGMENT, userMessageCode);
    }

    // ======================== @引用上下文 ========================

    /** @引用消息时前后各取的上下文条数 */
    public static final int QUOTE_CONTEXT_SIZE = 2;

    // ======================== 事实类型常量 ========================

    /** 用户偏好 */
    public static final String FACT_TYPE_PREFERENCE = "PREFERENCE";

    /** 事件 */
    public static final String FACT_TYPE_EVENT = "EVENT";

    /** 关系 */
    public static final String FACT_TYPE_RELATION = "RELATION";

    // ======================== L3 知识库记忆 ========================

    /** L3 每次检索最多返回的片段数 */
    public static final int L3_RETRIEVAL_TOPK = 3;

    /** L3 高置信度阈值：score >= 此值时，作为强约束注入（告诉 LLM 严格基于知识库回答） */
    public static final double L3_HIGH_THRESHOLD = 0.90;

    /** L3 低置信度阈值：score < 此值时，不注入，避免噪声干扰 */
    public static final double L3_LOW_THRESHOLD = 0.75;

    /** L3 高置信度注入前缀：告诉 LLM 必须基于知识库回答 */
    public static final String L3_HIGH_CONFIDENCE_PREFIX = "[请严格基于以下知识库内容回答，不要自由发挥]";

    /** L3 中等置信度注入前缀：知识库内容作为参考 */
    public static final String L3_MEDIUM_CONFIDENCE_PREFIX = "[以下知识库内容可能对你有帮助]";

    // ======================== 事实类型常量（保留向后兼容） ========================

    /**
     * 构建 memoryId。
     *
     * @param conversationCode 会话编码
     * @param ownerCode        Agent 编码
     * @return memoryId 字符串
     */
    public static String buildMemoryId(String conversationCode, String ownerCode) {
        return String.format(MEMORY_ID_TEMPLATE, conversationCode, ownerCode);
    }

    /**
     * 构建群聊 memoryId。
     *
     * @param conversationCode 会话编码
     * @param groupCode        群组编码
     * @param ownerCode        Agent 编码
     * @return memoryId 字符串
     */
    public static String buildGroupMemoryId(String conversationCode, String groupCode, String ownerCode) {
        return String.format(GROUP_MEMORY_ID_TEMPLATE, conversationCode, groupCode, ownerCode);
    }

    public static String buildWorkflowMemoryId(String conversationCode, String ownerCode, String workflowTaskId) {
        return String.format(WORKFLOW_MEMORY_ID_TEMPLATE, conversationCode, workflowTaskId, ownerCode);
    }

    /**
     * 从 memoryId 解析出 MemoryKey（含末尾锚定的 userMessageCode）。
     * <p>
     * memoryId 末尾可携带锚定用户消息 code 段（:um:{user_message_code}），
     * 解析时会剥离该段后再识别会话格式，并将 code 注入 MemoryKey。
     *
     * @param memoryId memoryId 字符串
     * @return MemoryKey，解析失败返回 null
     */
    public static MemoryKey parseMemoryId(Object memoryId) {
        if (memoryId == null) {
            return null;
        }
        String id = memoryId.toString();
        // 剥离末尾锚定用户消息 code 段：...:um:{user_message_code}
        String userMessageCode = null;
        int umIdx = id.lastIndexOf(":" + USER_MSG_CODE_SEGMENT + ":");
        if (umIdx > 0) {
            userMessageCode = id.substring(umIdx + 1 + USER_MSG_CODE_SEGMENT.length() + 1);
            id = id.substring(0, umIdx);
        }

        MemoryKey key = parseBaseMemoryId(id);
        if (key == null) {
            return null;
        }
        return userMessageCode == null || userMessageCode.isEmpty()
                ? key
                : new MemoryKey(key.getConversationCode(), key.getOwnerCode(), key.getGroupCode(), key.getTaskCode(), userMessageCode);
    }

    /**
     * 从完整 memoryId 中提取稳定身份 MemoryKey（不含 userMessageCode）。
     * 用于快照、移出检测等需要保持"存储空间"稳定的场景。
     */
    public static MemoryKey parseStableMemoryId(Object memoryId) {
        if (memoryId == null) {
            return null;
        }
        return parseBaseMemoryId(memoryId.toString().split(":" + USER_MSG_CODE_SEGMENT + ":")[0]);
    }

    /**
     * 解析不含锚定 code 段的纯会话 memoryId。
     */
    private static MemoryKey parseBaseMemoryId(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        // 格式1: conv:{conversation_code}:agent:{owner_code}
        String[] parts = id.split(":");
        if (parts.length >= 4 && "conv".equals(parts[0])) {
            // 群聊格式: conv:{conv_code}:group:{group_code}:agent:{owner_code}
            if (parts.length >= 6 && "group".equals(parts[2]) && "agent".equals(parts[4])) {
                return new MemoryKey(parts[1], parts[5], parts[3], null);
            }

            // 工作格式：conv:{conv_code}:task:{task_id}:agent:{owner_code}
            if (parts.length >= 6 && "task".equals(parts[2]) && "agent".equals(parts[4])) {
                return new MemoryKey(parts[1], parts[5], null, parts[3]);
            }

            // 单聊格式: conv:{conv_code}:agent:{owner_code}
            if ("agent".equals(parts[2])) {
                return new MemoryKey(parts[1], parts[3]);
            }
        }
        return null;
    }
}

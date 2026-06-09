package com.xiaomizhou.dpsk.memory.generator;

import java.util.List;

/**
 * L1 摘要生成器接口。
 * <p>
 * 使用 LLM 将旧摘要与新对话合并为增量摘要。
 * 具体实现由 opc-im 基于 LangChain4j 提供。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/29
 */
public interface SummaryGenerator {

    /**
     * 生成增量摘要。
     *
     * @param agentName       Agent 名称（用于第一人称叙述）
     * @param previousSummary 旧摘要文本，无则传 null
     * @param evictedMessages 被移出窗口的消息文本列表
     * @return 合并后的新摘要
     */
    String generateIncrementalSummary(String agentName, String previousSummary,
                                      List<String> evictedMessages);
}

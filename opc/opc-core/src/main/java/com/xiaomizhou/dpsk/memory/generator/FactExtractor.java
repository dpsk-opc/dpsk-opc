package com.xiaomizhou.dpsk.memory.generator;

import com.xiaomizhou.dpsk.memory.model.ExtractedFact;

import java.util.List;

/**
 * L2 事实提取器接口。
 * <p>
 * 使用 LLM 从对话中提取值得长期记住的事实。
 * 具体实现由 opc-im 基于 LangChain4j 提供。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/29
 */
public interface FactExtractor {

    /**
     * 从消息列表中提取事实。
     *
     * @param messages 消息文本列表（格式化后的对话文本）
     * @return 提取的事实列表
     */
    List<ExtractedFact> extractFacts(List<String> messages);
}

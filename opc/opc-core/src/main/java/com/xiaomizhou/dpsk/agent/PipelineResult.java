package com.xiaomizhou.dpsk.agent;

import dev.langchain4j.model.output.TokenUsage;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * AgentPipeline 的执行结果。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/3
 */
@Data
@Builder
public class PipelineResult {

    /** 是否执行成功 */
    private boolean success;

    /** 最终输出文本 */
    private String outputText;

    /** Token 用量 */
    private TokenUsage tokenUsage;

    /** 扩展元数据 */
    private Map<String, Object> meta;
}

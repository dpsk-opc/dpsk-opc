package com.xiaomizhou.dpsk.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.V;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/16 11:02
 * @description
 */
public interface StreamingReviewedWriter {
    @Agent
    TokenStream writeStory(@V("topic") String topic, @V("audience") String audience, @V("style") String style);
}

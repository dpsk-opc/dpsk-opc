package com.xiaomizhou.dpsk.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/16 11:07
 * @description
 */
public interface AudienceEditor {

    @UserMessage("""
        You are a professional editor.
        Analyze and rewrite the following story to better align
        with the target audience of {{audience}}.
        Return only the story and nothing else.
        The story is "{{story}}".
        """)
    @Agent("Edits a story to better fit a given audience")
    TokenStream editStory(@V("story") String story, @V("audience") String audience);
}

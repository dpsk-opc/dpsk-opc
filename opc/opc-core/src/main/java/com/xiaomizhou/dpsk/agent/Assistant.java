package com.xiaomizhou.dpsk.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/15 15:47
 * @description
 */
public interface Assistant  {

    @SystemMessage("You are a good friend of mine. Answer using slang.")
    TokenStream chat(String userMessage);

}

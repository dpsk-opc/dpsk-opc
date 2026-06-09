package com.xiaomizhou.dpsk.agent.data;

import com.xiaomizhou.dpsk.memory.assembler.ContextAssembler;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

public interface MemoryStore {


    ChatMemoryStore getChatMemoryStore();

    ContextAssembler getContextAssembler();

}

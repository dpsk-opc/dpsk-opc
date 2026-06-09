package com.xiaomizhou.dpsk.db;

import com.xiaomizhou.dpsk.agent.data.MemoryStore;
import com.xiaomizhou.dpsk.memory.MemorySystem;
import com.xiaomizhou.dpsk.memory.assembler.ContextAssembler;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ImMemoryStore — opc-im 层对 MemoryStore 接口的实现。
 * 基于 MemorySystem 提供 ChatMemoryStore 和 ContextAssembler。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/3
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ImMemoryStore implements MemoryStore {

    private final MemorySystem memorySystem;

    @Override
    public ChatMemoryStore getChatMemoryStore() {
        return memorySystem.getChatMemoryStore();
    }

    @Override
    public ContextAssembler getContextAssembler() {
        return memorySystem.getContextAssembler();
    }
}

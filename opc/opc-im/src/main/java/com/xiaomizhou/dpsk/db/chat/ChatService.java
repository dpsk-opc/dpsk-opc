package com.xiaomizhou.dpsk.db.chat;

import com.google.common.collect.Maps;
import com.xiaomizhou.dpsk.constant.ConversationType;
import com.xiaomizhou.dpsk.core.exceptions.BusinessException;
import com.xiaomizhou.dpsk.core.utils.WsUtils;
import com.xiaomizhou.dpsk.core.ws.SenderInfo;
import com.xiaomizhou.dpsk.core.ws.WsMessage;
import com.xiaomizhou.dpsk.core.ws.WsMsgType;
import com.xiaomizhou.dpsk.core.ws.payload.MessagePayload;
import com.xiaomizhou.dpsk.core.ws.payload.StreamChunkPayload;
import com.xiaomizhou.dpsk.core.ws.payload.StreamEndPayload;
import com.xiaomizhou.dpsk.core.ws.payload.StreamStartPayload;
import com.xiaomizhou.dpsk.db.AgentComponent;
import com.xiaomizhou.dpsk.db.ChatGroupComponent;
import com.xiaomizhou.dpsk.db.ChatMessageComponent;
import com.xiaomizhou.dpsk.db.dao.TokenUsageDao;
import com.xiaomizhou.dpsk.db.dto.AgentDto;
import com.xiaomizhou.dpsk.db.dto.ChatMemberDto;
import com.xiaomizhou.dpsk.db.dto.ChatMsgDto;
import com.xiaomizhou.dpsk.memory.MemorySystem;
import com.xiaomizhou.dpsk.memory.assembler.ContextAssembler;
import com.xiaomizhou.dpsk.memory.assembler.ContextAssembler.AssembledPrompt;
import com.xiaomizhou.dpsk.memory.config.MemoryConfig;
import com.xiaomizhou.dpsk.tool.LangChain4JToolBridge;
import com.xiaomizhou.dpsk.tool.ToolInvocationInterceptor;
import com.xiaomizhou.dpsk.tool.ToolRegistry;
import com.xiaomizhou.dpsk.tool.buildin.DateTimeTools;
import com.xiaomizhou.dpsk.utils.AgentUtils;
import com.xiaomizhou.dpsk.utils.SequenceUtils;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.TokenStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/16 15:26
 * @description
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ChatService {

    private final AgentComponent agentComponent;

    private final TokenUsageDao tokenUsageDao;

    private final ChatMessageComponent chatMessageComponent;

    private final ChatGroupComponent chatGroupComponent;

    /**
     * 上下文记忆系统（L0+L1+L2），由 MemoryConfiguration 自动装配
     */
    private final MemorySystem memorySystem;

    private final ToolRegistry toolRegistry;

    private final ToolInvocationInterceptor toolInvocationInterceptor;

    private final ApplicationContext applicationContext;

    private final AgentBridge agentBridge;

    /**
     * 聊天入口
     *
     * @param msgCode
     * @param userId  发送
     */
    public void doChat(String userId, String msgCode,List<String> mcpCodes) {

        if (StringUtils.isBlank(msgCode)) {
            return;
        }
        agentBridge.dispatch(userId, msgCode, mcpCodes);
    }
}

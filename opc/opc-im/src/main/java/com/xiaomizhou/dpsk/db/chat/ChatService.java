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
    public void doChat(String userId, String msgCode) {

        if (StringUtils.isBlank(msgCode)) {
            return;
        }

//        com.xiaomizhou.dpsk.db.model.ChatMessage msg = chatMessageComponent.getByCode(msgCode);
//        if (Objects.isNull(msg)) {
//            return;
//        }

//        String targetId = msg.getReceiverCode();
//        String conversationType = msg.getConversationType();
//
//        ImmutablePair<String, ConversationType> conv = chatMessageComponent.getConversationCode(msg.getSenderCode(), msg.getReceiverCode());
//        if (Objects.isNull(conv.left) || Objects.isNull(conv.right)) {
//            return;
//        }

        agentBridge.dispatch(userId, msgCode);



//
//        if (ConversationType.GROUP.name().equalsIgnoreCase(conversationType)) {
//
//            List<ChatMemberDto> members = chatGroupComponent.getGroupMembers(targetId);
//
//            if (CollectionUtils.isEmpty(members)) {
//                return;
//            }
//
//            // excludes input user.
//            members = members.stream().filter(member -> !member.getCode().equalsIgnoreCase(userId)).collect(Collectors.toList());
//            if (CollectionUtils.isEmpty(members)) {
//                return;
//            }
//
//            List<AgentDto> agents = agentComponent.getByCodes(members.stream().map(ChatMemberDto::getCode).collect(Collectors.toList()));
//
//            OpenAiChatModel model = OpenAiChatModel.builder()
//                    .modelName("deepseek-chat")
//                    .baseUrl("https://api.deepseek.com/v1")
//                    .apiKey("sk-0803dabfa90b4a188e116e007f442a62")
//                    .logRequests(true)
//                    .build();
//
//            var subagents = agents.stream().map(agent -> {
//                // ===== 群聊记忆：每个 Agent 在群聊中有独立的记忆空间 =====
//                // 使用标准群聊 memoryId 格式，L0/L1/L2 全栈支持
//                Object groupMemoryId = MemoryConfig.buildGroupMemoryId(
//                        conv.left, targetId, agent.getCode());
//                ChatMemory groupChatMemory = MessageWindowChatMemory.builder()
//                        .maxMessages(MemoryConfig.L0_MAX_MESSAGES)
//                        .chatMemoryStore(memorySystem.getChatMemoryStore())
//                        .id(groupMemoryId)
//                        .build();
//
//                // ===== 使用 ContextAssembler 注入 L2 长期事实 =====
//                ContextAssembler assembler = memorySystem.getContextAssembler();
//                String enrichedPersona = assembler.enrichSystemPrompt(AgentUtils.toAgentDef(agent), agent.getCode(), targetId);
//
//                return AgenticServices.agentBuilder()
//                        .chatModel(model)
//                        .name(agent.getCode())
//                        .description(AgentUtils.toAgentDef(agent))
//                        .userMessage(msg.getContent())
//                        .chatMemory(groupChatMemory)
//                        .toolProviders(LangChain4JToolBridge.forAgent(toolRegistry, toolInvocationInterceptor, applicationContext, agent.getCode()))
//                        .listener(new AgentListener() {
//                            @Override
//                            public void afterAgentInvocation(AgentResponse agentResponse) {
//                                String agentCode = agentResponse.agentName();
//                                Object output = agentResponse.output();
//                                ChatResponse response = agentResponse.chatResponse();
//
//                                TokenUsage token = agentResponse.chatResponse().tokenUsage();
//
//
//                                String mc = chatMessageComponent.newGroupChatMsg(agentCode, ChatMsgDto.builder()
//                                        .taskId(userId)
//                                        .sendId(agentCode)
//                                        .targetId(targetId)
//                                        .conversationType(ConversationType.GROUP.getCode())
//                                        .message(String.valueOf(output))
//                                        .messageType("AI")
//                                        .parentMsgCode("")
//                                        .taskId(msg.getTaskId())
//                                        .mentionedList(StringUtils.isBlank(msg.getMentionedList()) ? List.of() : Arrays.asList(msg.getMentionedList().split(",")))
//                                        .conversationCode(conv.left)
//
//                                        .build(), token, response.modelName());
//
//                                log.info("agent finished call. agentName:{},output:{}", agentCode, output);
//
//                                Map<String, Object> fullContent = Maps.newHashMap();
//                                com.xiaomizhou.dpsk.db.model.TokenUsage tk = tokenUsageDao.getOneByMsgCode(mc);
//                                fullContent.put("token", tk);
//
//                                AgentDto dto = agents.stream().filter(at -> Strings.CS.equals(at.getCode(), agentCode)).findAny().orElse(null);
//
//                                try {
//                                    WsUtils.send(new WsMessage("message",
//                                            new MessagePayload(mc, conv.left, "text",
//                                                    String.valueOf(output),
//                                                    Objects.isNull(dto) ? null : new SenderInfo(agent.getCode(), agent.getName(), agent.getAvatar()),
//                                                    System.currentTimeMillis(),
//                                                    "",
//                                                    fullContent
//                                            )));
//                                } catch (Exception e) {
//                                    log.warn("got an error.", e);
//                                }
//                            }
//
//
//                        })
//                        .systemMessage(enrichedPersona)
//                        .build();
//            }).toList();
//
//            SupervisorAgent supervisor = AgenticServices
//                    .supervisorBuilder()
//                    .chatModel(model)
//                    .subAgents(subagents)
//                    .responseStrategy(SupervisorResponseStrategy.SUMMARY)
//                    .build();
//
//
//            String response = supervisor.invoke(msg.getContent());
//
//            log.info("in group model, final response. response:{}", response);
//            return;
//        }
//
//
//        AgentDto agent = agentComponent.getByCode(targetId);
//        if (Objects.isNull(agent)) {
//            return;
//        }
//
//
//        Map<String, Object> map = Maps.newHashMap();
//
//        map.put("thinking", Map.of("type", "enabled"));
//
//        OpenAiStreamingChatModel model = OpenAiStreamingChatModel.builder()
//                .apiKey("sk-0803dabfa90b4a188e116e007f442a62")
//                .baseUrl("https://api.deepseek.com/v1")
//                .modelName("deepseek-chat")
//                .logRequests(true)
//                .logResponses(true)
//                .returnThinking(true)
//                .sendThinking(true)
//                .customParameters(map)
//                .build();
//
//        String agentName = agent.getName();
//
//        // ===== 使用 ContextAssembler 构建增强 System Prompt =====
//        // 将 L2 长期事实注入 system message，让 Agent 了解用户偏好和历史
//        String basePersona = AgentUtils.toAgentDef(agent);
//        ContextAssembler assembler = memorySystem.getContextAssembler();
//        AssembledPrompt enrichedPrompt = assembler.assemble(
//                basePersona, msg.getContent(), userId, targetId, conv.left, null);
//
//        // TODO get skills
////        List<String> skills = agent.getSkills();
//
//        // ===== 使用记忆系统构建 MessageWindowChatMemory =====
//        // memoryId 格式：conv:{conversation_code}:agent:{agent_code}
//        // 实现多 Agent 记忆隔离：每个 Agent 在同一个会话中有独立的记忆空间
//        Object memoryId = MemoryConfig.buildMemoryId(conv.left, targetId);
//
//        // 使用 MessageWindowChatMemory + DatabaseChatMemoryStore 实现 L0 工作记忆
//        // 当消息超出窗口时，DatabaseChatMemoryStore 自动触发 L1/L2 异步更新
//        ChatMemory chatMemory = MessageWindowChatMemory.builder()
//                .maxMessages(MemoryConfig.L0_MAX_MESSAGES)
//                .chatMemoryStore(memorySystem.getChatMemoryStore())
//                .id(memoryId)
//                .build();
//
//        var instance = AgenticServices.agentBuilder()
//                .streamingChatModel(model)
//                .name(agentName)
//                .systemMessage(enrichedPrompt.getSystemPart())
//                .toolProviders(LangChain4JToolBridge.forAgent(toolRegistry, toolInvocationInterceptor, applicationContext, agent.getCode()))
//                .tools()
//                .userMessage(msg.getContent())
//                .chatMemory(chatMemory)
//                .returnType(TokenStream.class)
//                .maxToolCallingRoundTrips(10)
//                .build();
//
//        String streamcode = SequenceUtils.generator().next("STM");
//        SenderInfo senderInfo = new SenderInfo(agent.getCode(), agent.getName(), agent.getAvatar());
//
//        AtomicInteger index = new AtomicInteger(0);
//        TokenStream stream = (TokenStream) instance.invoke(Map.of());
//
//        stream.onPartialThinking(response -> {
//
//            WsMessage m = new WsMessage(WsMsgType.STREAM_START,
//                    new StreamStartPayload(streamcode,
//                            conv.left,
//                            senderInfo, System.currentTimeMillis()));
//
//            try {
//                if (0 == index.get()) {
//                    WsUtils.send(m);
//                }
//
//                WsUtils.send(new WsMessage(WsMsgType.STREAM_CHUNK,
//                        new StreamChunkPayload(streamcode,
//                                response.text(), index.addAndGet(1))));
//            } catch (Exception e) {
//                log.error("ws closed!", e);
//            }
//
//        }).onError(error -> {
//            log.error("error:", error);
//        }).onCompleteResponse(response -> {
//
//            String content = response.aiMessage().text();
//
////            // save msg
//            com.xiaomizhou.dpsk.db.model.ChatMessage m = new com.xiaomizhou.dpsk.db.model.ChatMessage();
//
//            // save token
//            TokenUsage token = response.tokenUsage();
//
//            String replayMsgCode = chatMessageComponent.newSingleChatMsg(targetId, ChatMsgDto.builder()
//                    .targetId(userId)
//                    .sendId(targetId)
//                    .message(content)
//                    .messageType("AI")
//                    .conversationType(ConversationType.SINGLE.getCode())
//                    .build(), token, response.modelName());
//
//
//            Map<String, Object> fullContent = Maps.newHashMap();
//
//            com.xiaomizhou.dpsk.db.model.TokenUsage tk = tokenUsageDao.getOneByMsgCode(replayMsgCode);
//
//            fullContent.put("token", tk);
//
//            try {
//                WsUtils.send(new WsMessage(WsMsgType.STREAM_END, new StreamEndPayload(streamcode, null, null)));
//                WsUtils.send(new WsMessage(WsMsgType.MESSAGE, new MessagePayload(streamcode,
//                        conv.left,
//                        "text",
//                        content,
//                        senderInfo,
//                        System.currentTimeMillis(),
//                        "",
//                        fullContent)));
//            } catch (Exception e) {
//                log.error("ws closed!", e);
//                throw BusinessException.internalError("WebSocket 推送失败", e);
//            }
//
//        });
//        stream.start();
    }
}

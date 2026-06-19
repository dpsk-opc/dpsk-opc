package com.xiaomizhou.dpsk.agent.builder;

import com.google.common.collect.Maps;
import com.xiaomizhou.dpsk.agent.*;
import com.xiaomizhou.dpsk.agent.data.AgentDef;
import com.xiaomizhou.dpsk.agent.data.AgentDefProvider;
import com.xiaomizhou.dpsk.agent.event.AgentEvent;
import com.xiaomizhou.dpsk.agent.event.AgentEventType;
import com.xiaomizhou.dpsk.agent.factory.AgentComponentFactory;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 群聊模式 Builder。
 * 为每个 Sub-Agent 构建独立的 Pipeline，使用 SupervisorAgent 协调。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/3
 */
@Slf4j
@RequiredArgsConstructor
public class GroupBuilder implements AgentBuilder {

    private final AgentDefProvider agentDefProvider;
    private final AgentComponentFactory factory;

    private final Object lock = new Object();

    @Override
    public String supportedMode() {
        return AgentBuildSpec.MODE_GROUP;
    }

    @Override
    public AgentPipeline build(AgentBuildSpec spec) {
        List<String> targetAgentCodes = spec.getTargetAgentCodes();
        if (targetAgentCodes == null || targetAgentCodes.isEmpty()) {
            throw new IllegalArgumentException("GroupBuilder requires targetAgentCodes");
        }

        // 1. 批量查询 Agent 定义
        List<AgentDef> agentDefs = agentDefProvider.getByCodes(targetAgentCodes);
        if (agentDefs.isEmpty()) {
            throw new IllegalArgumentException("No agents found for codes: " + targetAgentCodes);
        }

        // 2. 构建默认同步 LLM 模型（Supervisor 和无自定义配置的 Sub-Agent 使用）
        OpenAiChatModel defaultModel = factory.createChatModel();


        // 群聊模式，每个人自己回复
        return new GroupPipeline(null, agentDefs, spec.getUserContent(), callback -> {
            // 3. 为每个 Sub-Agent 构建 AgenticServices Agent
            var subAgents = agentDefs.stream().map(agentDef -> {
                // 群聊记忆：每个 Agent 在群聊中有独立的记忆空间
                ChatMemory groupChatMemory = factory.createChatMemory(agentDef, spec);

                // 注入 L2 长期事实
                String enrichedPersona = factory.enrichSystemPrompt(agentDef, spec.getGroupCode());

                // 为有自定义 LLM 配置的 Agent 创建独立模型
                OpenAiChatModel agentModel = defaultModel;
                if (agentDef.getLlmConfig() != null && !agentDef.getLlmConfig().isBlank()) {
                    agentModel = factory.createChatModel(agentDef.getLlmConfig());
                }

                return AgenticServices.agentBuilder()
                        .chatModel(agentModel)
                        .name(agentDef.getCode())
                        .description(enrichedPersona)
                        .userMessage(spec.getUserContent())
                        .listener(new AgentListener() {
                            @Override
                            public void afterAgentInvocation(AgentResponse agentResponse) {

                                if (!(callback instanceof GroupAgentCallback groupAgentCallback)) {
                                    return;
                                }

                                synchronized (lock) {
                                    groupAgentCallback.setStreamCode(UUID.randomUUID().toString().replace("-", ""));
                                    groupAgentCallback.setSenderInfo(agentDef.getCode());

                                    String text = agentResponse.chatResponse().aiMessage().text();

                                    Map<String, Object> meta = Maps.newHashMap();

                                    meta.put("content", text);
                                    meta.put("tokenUsage", agentResponse.chatResponse().tokenUsage());

                                    AgentEvent event = new AgentEvent(AgentEventType.DONE, agentDef.getCode(), text, null, null, null, meta);
                                    groupAgentCallback.onEvent(event);
                                }
                            }
                        })
                        .chatMemory(groupChatMemory)
                        .toolProviders(factory.getToolProviders(agentDef.getCode(), spec.getUserCode(), spec.getConversationCode(), spec.getMcpCodes()))
                        .systemMessage(enrichedPersona)
                        .build();
            }).collect(Collectors.toList());

            // 4. 构建 SupervisorAgent（使用默认模型）
            return AgenticServices
                    .supervisorBuilder()
                    .chatModel(defaultModel)
                    .subAgents(subAgents)
                    .responseStrategy(SupervisorResponseStrategy.SUMMARY)
                    .build();
        });
    }

    /**
     * 群聊 Pipeline 实现。
     */
    private static class GroupPipeline implements AgentPipeline {

        private final SupervisorAgent supervisor;
        private final List<AgentDef> agentDefs;
        private final String userContent;

        private final Function<AgentCallback,SupervisorAgent> agentCallback;

        GroupPipeline(SupervisorAgent supervisor, List<AgentDef> agentDefs, String userContent, Function<AgentCallback,SupervisorAgent> callback) {
            this.supervisor = supervisor;
            this.agentDefs = agentDefs;
            this.userContent = userContent;
            this.agentCallback = callback;
        }

        @Override
        public PipelineResult execute(AgentCallback callback) {
            try {

                if(Objects.nonNull(supervisor)) {

                    String response = supervisor.invoke(userContent);

                    Map<String, Object> meta = new HashMap<>();
                    meta.put("content", response);
                    meta.put("agentCount", agentDefs.size());

                    // 发送 DONE 事件（群聊没有流式输出，直接返回结果）
                    callback.onEvent(AgentEvent.done("supervisor", meta));
                    callback.onComplete();

                    return PipelineResult.builder()
                            .success(true)
                            .outputText(response)
                            .meta(meta)
                            .build();
                } else {
                    SupervisorAgent apply = agentCallback.apply(callback);
                    String response = apply.invoke(userContent);
                    return PipelineResult.builder()
                            .success(true)
                            .outputText(response)
                            .build();
                }

            } catch (Exception e) {
                log.error("GroupPipeline execution failed", e);
//                callback.onEvent(AgentEvent.error("supervisor", e.getMessage()));
//                callback.onError(e);
                return PipelineResult.builder()
                        .success(false)
                        .outputText(null)
                        .build();
            }
        }
    }
}

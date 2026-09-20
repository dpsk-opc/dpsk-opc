package com.xiaomizhou.dpsk.tool.workspace;

import com.xiaomizhou.dpsk.agent.factory.AgentComponentFactory;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 路径抽取的 LLM 实现，复用 {@link AgentComponentFactory#createChatModel()} 配置（D17）。
 * <p>
 * <b>防注入</b>：输入仅包含工具名 + 描述 + 参数 JSON（由 {@link PathExtractor} 组装），
 * 绝不包含外部文件内容、历史消息等不可信内容。
 * <p>
 * <b>循环依赖</b>：使用 {@link ObjectProvider} 懒加载 {@code AgentComponentFactory}，
 * 打破
 * {@code AgentComponentFactory → ToolInvocationInterceptor → PathAccessValidator
 *  → PathExtractor → WorkspacePathExtractionLlm → AgentComponentFactory}
 * 的构造期循环依赖（与该类中 {@code CapabilityExtractor} 的处理方式一致）。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/9/20
 */
@Slf4j
@Component
public class WorkspacePathExtractionLlm implements PathExtractionLlm {

    private final ObjectProvider<AgentComponentFactory> agentComponentFactoryProvider;

    public WorkspacePathExtractionLlm(ObjectProvider<AgentComponentFactory> agentComponentFactoryProvider) {
        this.agentComponentFactoryProvider = agentComponentFactoryProvider;
    }

    @Override
    public String chat(String prompt) {
        try {
            AgentComponentFactory factory = agentComponentFactoryProvider.getIfAvailable();
            if (factory == null) {
                log.warn("AgentComponentFactory not available, path extraction skipped");
                return null;
            }
            ChatModel model = factory.createChatModel();
            ChatRequest request = ChatRequest.builder()
                    .messages(UserMessage.from(prompt))
                    .build();
            ChatResponse response = model.chat(request);
            return response == null || response.aiMessage() == null ? null : response.aiMessage().text();
        } catch (Exception e) {
            log.warn("Path extraction llm call failed", e);
            return null;
        }
    }
}

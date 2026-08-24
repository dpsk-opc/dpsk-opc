package com.xiaomizhou.dpsk.memory.assembler;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.xiaomizhou.dpsk.agent.AgentBuildSpec;
import com.xiaomizhou.dpsk.agent.data.AgentDef;
import com.xiaomizhou.dpsk.agent.data.AgentDefProvider;
import com.xiaomizhou.dpsk.memory.PersonaProvider;
import com.xiaomizhou.dpsk.memory.config.MemoryConfig;
import com.xiaomizhou.dpsk.memory.manager.FactManager;
import com.xiaomizhou.dpsk.memory.manager.KnowledgeManager;
import com.xiaomizhou.dpsk.memory.manager.SummaryManager;
import com.xiaomizhou.dpsk.memory.model.MemoryFragment;
import com.xiaomizhou.dpsk.memory.repository.MessageRepository;
import com.xiaomizhou.dpsk.tool.SourceType;
import com.xiaomizhou.dpsk.tool.ToolRegistry;
import com.xiaomizhou.dpsk.tool.model.ToolMetadata;
import com.xiaomizhou.dpsk.utils.MemoryUtils;
import dev.langchain4j.data.message.*;
import dev.langchain4j.skills.FileSystemSkill;
import dev.langchain4j.skills.FileSystemSkillLoader;
import dev.langchain4j.skills.Skills;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.xiaomizhou.dpsk.tool.LangChain4JToolBridge.ADD_TOOLS_TOOL_NAME;

/**
 * 上下文组装引擎。
 * <p>
 * 按照 人设 → L3(知识库) → L1(摘要) → @引用 → L2(语义检索) → L0(工作记忆) → 当前消息 的顺序
 * 组装最终发给 LLM 的 Prompt。
 * <p>
 * L2 已改为纯 RAG 模式：不再"全量注入"长期事实到 system prompt，
 * 而是在 history 部分按需语义检索 Top-K 相关历史消息。
 * <p>
 * 无状态设计，每次调用 assemble() 都会实时查询各层记忆。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/29
 */
@Slf4j
public class ContextAssembler {

    private final MessageRepository messageRepository;
    private final SummaryManager summaryManager;
    private final FactManager factManager;
    private final KnowledgeManager knowledgeManager;
    private final AgentDefProvider agentDefProvider;
    private final ToolRegistry toolRegistry;

    public ContextAssembler(MessageRepository messageRepository,
                            SummaryManager summaryManager,
                            FactManager factManager,
                            KnowledgeManager knowledgeManager,
                            AgentDefProvider provider,
                            ToolRegistry toolRegistry) {
        this.messageRepository = Objects.requireNonNull(messageRepository, "messageRepository must not be null");
        this.summaryManager = summaryManager;
        this.factManager = factManager;
        this.knowledgeManager = knowledgeManager;
        this.agentDefProvider = provider;
        this.toolRegistry = toolRegistry;
    }

    public AssembledPrompt assembledForWorkflow(AgentBuildSpec spec){
        StringBuilder historyPart = new StringBuilder();

        // === System 部分 ===

        String targetAgentCode = spec.getTargetAgentCode();
        // 1. Agent 人设
        List<AgentDef> agents = agentDefProvider.getByCodes(List.of(targetAgentCode, spec.getUserCode()));

        StringBuffer sb = new StringBuffer();
        if (CollectionUtils.isNotEmpty(agents)) {

            AgentDef target = agents.stream().filter(a -> a.getCode().equals(targetAgentCode)).findFirst().orElse(null);

            // ai的信息
            if (Objects.nonNull(target)) {
                sb.append("[你的信息] 名字:%s,你先出处于任务模式，使用客观的描述说明你已经完成的工作，后续节点需要根据你的输出开展后续的工作。prompt:%s\n".formatted(target.getName(), spec.getPrompt()));
            }
        }

        // 工具信息
        List<ToolMetadata> tools = toolRegistry.getToolsForAgent(spec.getTargetAgentCode());
        if (CollectionUtils.isNotEmpty(tools)) {
            if (CollectionUtils.isNotEmpty(spec.getMcpCodes())) {
                tools = tools.stream().filter(t -> SourceType.MCP.equalsIgnoreCase(t.getSourceType())).filter(t -> {
                    return spec.getMcpCodes().contains(t.getSourceRef().split(":")[0]);
                }).collect(Collectors.toList());
            }
            if (CollectionUtils.isNotEmpty(tools)) {
                sb.append("[Tools] 你有这些工具可以用。注意：这些工具只是摘要，需要先使用%s方法将工具添加到工具列表中才可以使用！！\n".formatted(ADD_TOOLS_TOOL_NAME));
                sb.append(tools.stream().map(t -> t.getName() + ":" + t.getDescription()).collect(Collectors.joining("\n")));
            }
        }

        // skills
        if (CollectionUtils.isNotEmpty(spec.getSkillPaths())) {
            sb.append("[Skills] 你有这些技能可以用。注意：这些技能只是摘要，要使用工具加载！！\n");
            for (String skillPath : spec.getSkillPaths()) {
                try {
                    FileSystemSkill skill = FileSystemSkillLoader.loadSkill(Path.of(skillPath));
                    sb.append("skillPath:%s,description:%s".formatted(skillPath, skill.description()));
                    if (CollectionUtils.isNotEmpty(skill.resources())) {
                        List<String> resources = skill.resources().stream().map(r -> "reletivepath:%s,content:%s".formatted(r.relativePath(), r.content())).collect(Collectors.toList());
                        sb.append("resources:%s".formatted(Joiner.on(",").join(resources)) + "\n");
                    } else {
                        sb.append("\n");
                    }
                } catch (Exception e) {
                    log.warn("load skills error!", e);
                }
            }
        }


        String userContent = spec.getUserContent();
        String userCode = spec.getUserCode();
        String conversationCode = spec.getConversationCode();
        String quoteMessageCode = spec.getQuoteMessageCode();
        String agentCode = spec.getTargetAgentCode();

        // 2. L3 知识库记忆（向量检索 + 分级注入） TODO 替换成项目知识库
        if (knowledgeManager != null) {
            KnowledgeManager.L3Result l3Result = knowledgeManager.retrieve(userContent, agentCode);
            if (l3Result.shouldInject()) {
                sb.append(l3Result.getInjectText());
            }
        }


        // === History 部分 ===

        // 3. L1 摘要（若有）
        if (summaryManager != null) {
            String summary = summaryManager.getLatestSummary(conversationCode, agentCode);
            if (summary != null && !summary.isEmpty()) {
                historyPart.append("[近期往事] ").append(summary).append("\n\n");
            }
        }

        // 4. @历史消息引用
        if (quoteMessageCode != null && !quoteMessageCode.isEmpty()) {
            ChatMessage quoted = messageRepository.findByCode(quoteMessageCode);
            if (quoted != null) {
                List<ChatMessage> context = messageRepository.findContext(
                        quoteMessageCode, MemoryConfig.QUOTE_CONTEXT_SIZE);
                historyPart.append("[被引用的对话记录]\n");
                for (ChatMessage m : context) {
                    historyPart.append(formatChatMessage(m)).append("\n");
                }
                historyPart.append("\n");
            }
        }

        // 5. L2 语义检索（纯 RAG，按需检索 Top-K 历史消息）
        if (factManager != null) {
            List<MemoryFragment> retrieved = factManager.retrieveMemories(
                    userContent, agentCode, userCode, MemoryConfig.L2_RETRIEVAL_TOPK);
            if (!retrieved.isEmpty()) {
                historyPart.append("[相关历史消息]\n");
                for (MemoryFragment f : retrieved) {
                    historyPart.append("- ").append(f.getText()).append("\n");
                }
                historyPart.append("\n");
            }
        }

        // 5.1 注入定时任务锚点上下文（如果有）
        if (spec.getTaskContext() != null && !spec.getTaskContext().isEmpty()) {
            sb.append(spec.getTaskContext());
        }

        return new AssembledPrompt(sb.toString(), historyPart.toString());
    }


    /**
     * 组装完整上下文 Prompt。
     *
     * @param spec 构建规范
     * @return 组装后的 Prompt 文本
     */
    public AssembledPrompt assemble(AgentBuildSpec spec) {

        StringBuilder historyPart = new StringBuilder();

        // === System 部分 ===

        String targetAgentCode = spec.getTargetAgentCode();
        // 1. Agent 人设
        List<String> agentCodes = Lists.newArrayList();
        agentCodes.add(spec.getUserCode());
        if(StringUtils.isNotBlank(spec.getTargetAgentCode())){
            agentCodes.add(targetAgentCode);
        }
        List<AgentDef> agents = agentDefProvider.getByCodes(agentCodes);

        StringBuffer sb = new StringBuffer();
        if (CollectionUtils.isNotEmpty(agents)) {

            AgentDef user = agents.stream().filter(a -> a.getCode().equals(spec.getUserCode())).findFirst().orElse(null);
            AgentDef target = agents.stream().filter(a -> a.getCode().equals(targetAgentCode)).findFirst().orElse(null);

            // ai的信息
            if (Objects.nonNull(target)) {
                sb.append("[你的信息] 名字:%s,性别:%s,昵称:%s,prompt:%s".formatted(target.getName(), 1 == target.getSex() ? "男" : "女", target.getNickname(), target.getPrompt()));
            }

            // 对话的人的信息
            if (Objects.nonNull(user)) {
                sb.append("[跟你对话的人的信息] 名字:%s,性别:%s,昵称:%s".formatted(user.getName(), 1 == user.getSex() ? "男" : "女", user.getNickname()));
            }
        }

        // 工具信息
        List<ToolMetadata> tools = toolRegistry.getToolsForAgent(spec.getTargetAgentCode());
        if (CollectionUtils.isNotEmpty(tools)) {
            if (CollectionUtils.isNotEmpty(spec.getMcpCodes())) {
                tools = tools.stream().filter(t -> SourceType.MCP.equalsIgnoreCase(t.getSourceType())).filter(t -> {
                    return spec.getMcpCodes().contains(t.getSourceRef().split(":")[0]);
                }).collect(Collectors.toList());
            }
            if (CollectionUtils.isNotEmpty(tools)) {
                sb.append("[Tools] 你有这些工具可以用。注意：这些工具只是摘要，是没有办法直接使用的，需要先使用%s方法将工具添加到工具列表中才可以使用！！\n".formatted(ADD_TOOLS_TOOL_NAME));
                sb.append(tools.stream().map(t -> t.getName() + ":" + t.getDescription()).collect(Collectors.joining("\n")));
            }
        }

        // skills
        if (CollectionUtils.isNotEmpty(spec.getSkillPaths())) {
            sb.append("[Skills] 你有这些技能可以用。注意：这些技能只是摘要，是没有办法直接使用的，要使用工具加载！！\n");
            for (String skillPath : spec.getSkillPaths()) {
                try {
                    FileSystemSkill skill = FileSystemSkillLoader.loadSkill(Path.of(skillPath));
                    sb.append("skillPath:%s,description:%s".formatted(skillPath, skill.description()));
                    if (CollectionUtils.isNotEmpty(skill.resources())) {
                        List<String> resources = skill.resources().stream().map(r -> "reletivepath:%s,content:%s".formatted(r.relativePath(), r.content())).collect(Collectors.toList());
                        sb.append("resources:%s".formatted(Joiner.on(",").join(resources)) + "\n");
                    } else {
                        sb.append("\n");
                    }
                } catch (Exception e) {
                    log.warn("load skills error!", e);
                }
            }
        }


        String userContent = spec.getUserContent();
        String userCode = spec.getUserCode();
        String conversationCode = spec.getConversationCode();
        String quoteMessageCode = spec.getQuoteMessageCode();
        String agentCode = spec.getTargetAgentCode();

        // 2. L3 知识库记忆（向量检索 + 分级注入）
        if (knowledgeManager != null) {
            KnowledgeManager.L3Result l3Result = knowledgeManager.retrieve(userContent, agentCode);
            if (l3Result.shouldInject()) {
                sb.append(l3Result.getInjectText());
            }
        }


        // === History 部分 ===

        // 3. L1 摘要（若有）
        if (summaryManager != null) {
            String summary = summaryManager.getLatestSummary(conversationCode, agentCode);
            if (summary != null && !summary.isEmpty()) {
                historyPart.append("[近期往事] ").append(summary).append("\n\n");
            }
        }

        // 4. @历史消息引用
        if (quoteMessageCode != null && !quoteMessageCode.isEmpty()) {
            ChatMessage quoted = messageRepository.findByCode(quoteMessageCode);
            if (quoted != null) {
                List<ChatMessage> context = messageRepository.findContext(
                        quoteMessageCode, MemoryConfig.QUOTE_CONTEXT_SIZE);
                historyPart.append("[被引用的对话记录]\n");
                for (ChatMessage m : context) {
                    historyPart.append(formatChatMessage(m)).append("\n");
                }
                historyPart.append("\n");
            }
        }

        // 5. L2 语义检索（纯 RAG，按需检索 Top-K 历史消息）
        if (factManager != null) {
            List<MemoryFragment> retrieved = factManager.retrieveMemories(
                    userContent, agentCode, userCode, MemoryConfig.L2_RETRIEVAL_TOPK);
            if (!retrieved.isEmpty()) {
                historyPart.append("[相关历史消息]\n");
                for (MemoryFragment f : retrieved) {
                    historyPart.append("- ").append(f.getText()).append("\n");
                }
                historyPart.append("\n");
            }
        }

        // 5.1 注入定时任务锚点上下文（如果有）
        if (spec.getTaskContext() != null && !spec.getTaskContext().isEmpty()) {
            sb.append(spec.getTaskContext());
        }

        return new AssembledPrompt(sb.toString(), historyPart.toString());
    }



    /**
     * 仅注入 L2 语义检索结果到 system prompt（不包含 L0/L1 历史）。
     * <p>
     * 适用于群聊场景：群聊的 L0 历史由 MessageWindowChatMemory 管理，
     * L1 摘要通过 MemoryManager 异步生成，此处仅注入 L2 检索结果。
     *
     * @param systemPrompt Agent 人设
     * @param ownerCode    Agent 编码
     * @param targetCode   目标编码（单聊为用户编码，群聊为群组编码）
     * @return 增强后的 system prompt
     */
    public String enrichSystemPrompt(String systemPrompt, String ownerCode, String targetCode) {
        if (systemPrompt == null) {
            systemPrompt = "";
        }
        if (factManager == null) {
            return systemPrompt;
        }

        // 群聊场景：根据 targetCode 做一次 L2 语义检索
        StringBuilder sb = new StringBuilder(systemPrompt);
        List<MemoryFragment> retrieved = factManager.retrieveMemories(
                targetCode, ownerCode, targetCode, MemoryConfig.L2_RETRIEVAL_TOPK);
        if (!retrieved.isEmpty()) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append("[相关历史消息]\n");
            for (MemoryFragment f : retrieved) {
                sb.append("- ").append(f.getText()).append("\n");
            }
        }
        return sb.toString();
    }

    private String formatChatMessage(ChatMessage message) {
        if (message instanceof AiMessage) {
            return "AI: " + ((AiMessage) message).text();
        } else if (message instanceof UserMessage) {
           return MemoryUtils.toSingleContent((UserMessage) message);
        } else if (message instanceof SystemMessage) {
            return "[System]: " + ((SystemMessage) message).text();
        }
        return message.type().name() + ": " + message;
    }



    public static class AssembledPrompt {

        private final String systemPart;

        private final String historyPart;

        public AssembledPrompt(String systemPart, String historyPart) {
            this.systemPart = systemPart;
            this.historyPart = historyPart;
        }

        public String getSystemPart() {
            return systemPart;
        }

        public String getHistoryPart() {
            return historyPart;
        }



        /**
         * 获取完整的 Prompt 文本（System + History 合并）
         */
        public String getFullPrompt() {
            if (systemPart.isEmpty()) {
                return historyPart;
            }
            return systemPart + "\n" + historyPart;
        }
    }
}

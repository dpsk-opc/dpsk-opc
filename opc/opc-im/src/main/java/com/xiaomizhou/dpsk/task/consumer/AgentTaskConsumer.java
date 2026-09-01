package com.xiaomizhou.dpsk.task.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaomizhou.dpsk.agent.AgentBuildSpec;
import com.xiaomizhou.dpsk.agent.AgentOrchestrator;
import com.xiaomizhou.dpsk.agent.PipelineResult;
import com.xiaomizhou.dpsk.agent.data.AgentDefProvider;
import com.xiaomizhou.dpsk.constant.ConversationType;
import com.xiaomizhou.dpsk.db.AgentComponent;
import com.xiaomizhou.dpsk.db.ChatMessageComponent;
import com.xiaomizhou.dpsk.db.chat.ImAgentCallback;
import com.xiaomizhou.dpsk.db.dao.ConversationDao;
import com.xiaomizhou.dpsk.db.dao.TokenUsageDao;
import com.xiaomizhou.dpsk.db.dto.AgentDto;
import com.xiaomizhou.dpsk.db.dto.ChatMsgDto;
import com.xiaomizhou.dpsk.db.model.Conversation;
import com.xiaomizhou.dpsk.core.ws.SenderInfo;
import com.xiaomizhou.dpsk.memory.repository.MessageRepository;
import com.xiaomizhou.dpsk.task.TaskCreationContext;
import com.xiaomizhou.dpsk.task.model.Task;
import com.xiaomizhou.dpsk.task.model.TaskConsumeResult;
import com.xiaomizhou.dpsk.utils.SequenceUtils;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.output.TokenUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * AI 任务消费者 — 定时任务触发时执行 AI 流程。
 * <p>
 * 流程：
 * <ol>
 *   <li>解析 parameters 获取 userId、prompt、anchorMsgCode、contextSize</li>
 *   <li>通过 anchorMsgCode 查询创建任务时的对话上下文</li>
 *   <li>创建 ChatMessage 模拟用户触发 → 拿到 msgCode</li>
 *   <li>组装 AgentBuildSpec（含 taskContext） → 调用 AgentOrchestrator 执行</li>
 * </ol>
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/4
 */
@Slf4j
@RequiredArgsConstructor
public class AgentTaskConsumer implements TaskConsumer {

    public static final String CONSUMER_KEY = "AGENT_TASK";

    private final AgentOrchestrator orchestrator;
    private final MessageRepository messageRepository;
    private final ChatMessageComponent chatMessageComponent;
    private final AgentComponent agentComponent;
    private final AgentDefProvider agentDefProvider;
    private final TokenUsageDao tokenUsageDao;
    private final ConversationDao conversationDao;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getConsumerKey() {
        return CONSUMER_KEY;
    }

    @Override
    public TaskConsumeResult consume(Task task, Map<String, Object> triggerContext) {
        String taskCode = task.getCode();
        log.info("AgentTaskConsumer triggered: task={}", taskCode);

        try {
            // 1. 解析 parameters
            String parameters = task.getParameters();
            if (StringUtils.isBlank(parameters)) {
                return TaskConsumeResult.fail("Task parameters is empty");
            }

            JsonNode params = objectMapper.readTree(parameters);
            String userId = getString(params, "userId");
            String prompt = getString(params, "prompt");
            String anchorMsgCode = getString(params, "anchorMsgCode");
            int contextSize = params.has("contextSize") ? params.get("contextSize").asInt(10) : 10;

            if (StringUtils.isAnyBlank(userId, prompt)) {
                return TaskConsumeResult.fail("Missing required parameters: userId or prompt");
            }

            String agentCode = task.getAgentCode();
            String conversationCode = task.getConversationCode();

            // 2. 根据会话编码判断实际会话类型（专家团/群聊/单聊），而非写死 SINGLE，
            //    避免在专家团/群聊会话下错误创建出多余的单聊会话。
            ConversationType conversationType = resolveConversationType(conversationCode);

            // 3. 构建锚点上下文
            String taskContext = buildTaskContext(anchorMsgCode, contextSize, task.getName(), prompt);

            // 4. 创建 ChatMessage（模拟用户触发），按实际会话类型入库
            String triggerContent = "[定时任务: " + task.getName() + "]\n" + prompt;

            ChatMsgDto dto = ChatMsgDto.builder()
                    .targetId(agentCode)
                    .sendId(userId)
                    .message(triggerContent)
                    .messageType("USER")
                    .conversationType(conversationType.getCode())
                    .conversationCode(conversationCode)
                    .build();

            String msgCode = saveMessage(userId, dto, conversationType, null, null);

            if (StringUtils.isBlank(msgCode)) {
                return TaskConsumeResult.fail("Failed to create trigger message");
            }

            log.info("AgentTaskConsumer created trigger message: msgCode={}", msgCode);

            // 4. 获取 Agent 信息
            AgentDto agent = agentComponent.getByCode(agentCode);
            if (agent == null) {
                return TaskConsumeResult.fail("Agent not found: " + agentCode);
            }

            // 5. 组装 AgentBuildSpec
            AgentBuildSpec spec = AgentBuildSpec.builder()
                    .mode(AgentBuildSpec.MODE_SINGLE)
                    .userCode(userId)
                    .targetAgentCode(agentCode)
                    .userContent(triggerContent)
                    .conversationCode(conversationCode)
                    .taskContext(taskContext)
                    .build();

            // 6. 生成流式编码
            String streamCode = SequenceUtils.generator().next("STM");

            // 7. 创建回调（按实际会话类型，避免 Agent 回复被误落到单聊会话）
            SenderInfo senderInfo = new SenderInfo(agent.getCode(), agent.getName(), agent.getAvatar(), agent.getNickname());
            ImAgentCallback callback = new ImAgentCallback(
                    userId, conversationCode,
                    conversationType.name(), agentCode, taskCode,
                    chatMessageComponent, tokenUsageDao, agentDefProvider);
            callback.setStreamCode(streamCode);
            callback.setSenderInfo(senderInfo);

            // 8. 执行编排（设置 TaskCreationContext 透传参数给工具）
            TaskCreationContext.set(userId, agentCode, conversationCode);
            PipelineResult result;
            try {
                result = orchestrator.execute(spec, callback);
            } finally {
                TaskCreationContext.clear();
            }

            log.info("AgentTaskConsumer completed: task={}, success={}, contentLen={}",
                    taskCode, result.isSuccess(),
                    result.getOutputText() != null ? result.getOutputText().length() : 0);

            return result.isSuccess()
                    ? TaskConsumeResult.ok("AI task executed successfully, msgCode=" + msgCode)
                    : TaskConsumeResult.fail("AI task execution failed");

        } catch (Exception e) {
            log.error("AgentTaskConsumer failed: task={}", taskCode, e);
            return TaskConsumeResult.fail(e.getMessage());
        }
    }

    /**
     * 根据锚点消息编码查询创建任务时的对话上下文。
     */
    private String buildTaskContext(String anchorMsgCode, int contextSize,
                                     String taskName, String prompt) {
        if (StringUtils.isBlank(anchorMsgCode)) {
            return "[定时任务触发]\n任务: " + taskName + "\n指令: " + prompt
                    + "\n（无历史上下文记录）";
        }

        try {
            List<ChatMessage> context = messageRepository.findContext(anchorMsgCode, contextSize);
            if (context.isEmpty()) {
                return "[定时任务触发]\n任务: " + taskName + "\n指令: " + prompt
                        + "\n（无法加载上下文）";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("[定时任务触发]\n");
            sb.append("任务: ").append(taskName).append("\n");
            sb.append("以下是创建此任务时的对话上下文：\n");

            for (ChatMessage msg : context) {
                sb.append(formatMessage(msg)).append("\n");
            }

            sb.append("--- 上下文结束 ---\n");
            sb.append("请结合以上任务创建时的上下文和当前对话历史，执行此定时任务。");

            return sb.toString();
        } catch (Exception e) {
            log.warn("Failed to build task context for anchor={}", anchorMsgCode, e);
            return null;
        }
    }

    /**
     * 格式化单条消息。
     */
    private String formatMessage(ChatMessage message) {
        if (message instanceof dev.langchain4j.data.message.AiMessage) {
            return "AI: " + ((dev.langchain4j.data.message.AiMessage) message).text();
        } else if (message instanceof dev.langchain4j.data.message.UserMessage) {
            return "用户: " + ((dev.langchain4j.data.message.UserMessage) message).singleText();
        } else if (message instanceof dev.langchain4j.data.message.SystemMessage) {
            return "[System]: " + ((dev.langchain4j.data.message.SystemMessage) message).text();
        }
        return message.type().name() + ": " + message;
    }

    /**
     * 根据会话编码解析实际会话类型；查不到时回退为单聊。
     */
    private ConversationType resolveConversationType(String conversationCode) {
        if (StringUtils.isNotBlank(conversationCode)) {
            Conversation conv = conversationDao.getOneByCode(conversationCode);
            if (conv != null && conv.getConversationType() != null) {
                ConversationType type = ConversationType.getByCode(conv.getConversationType());
                if (type != null) {
                    return type;
                }
            }
        }
        return ConversationType.SINGLE;
    }

    /**
     * 按实际会话类型选择入库方法：
     * <ul>
     *   <li>WORKFLOW（专家团）→ newWorkflowMsg（复用现有会话，不新建）</li>
     *   <li>GROUP（群聊）→ newGroupChatMsg</li>
     *   <li>SINGLE（单聊）→ newSingleChatMsg</li>
     * </ul>
     */
    private String saveMessage(String sendId, ChatMsgDto dto, ConversationType type,
                               TokenUsage token, String modelName) {
        switch (type) {
            case WORKFLOW:
                return chatMessageComponent.newWorkflowMsg(sendId, dto, token, modelName);
            case GROUP:
                return chatMessageComponent.newGroupChatMsg(sendId, dto, token, modelName);
            case SINGLE:
            default:
                return chatMessageComponent.newSingleChatMsg(sendId, dto, token, modelName);
        }
    }

    private String getString(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }
}

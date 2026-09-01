package com.xiaomizhou.dpsk.tool.ask;

import com.xiaomizhou.dpsk.core.utils.WsUtils;
import com.xiaomizhou.dpsk.core.ws.WsMessage;
import com.xiaomizhou.dpsk.core.ws.WsMsgType;
import com.xiaomizhou.dpsk.core.ws.payload.ToolAskPayload;
import com.xiaomizhou.dpsk.db.dao.ConversationDao;
import com.xiaomizhou.dpsk.db.model.Conversation;
import com.xiaomizhou.dpsk.tool.ToolMeta;
import com.xiaomizhou.dpsk.tool.ToolCategory;
import com.xiaomizhou.dpsk.tool.ask.ToolAskManager.AskContext;
import com.xiaomizhou.dpsk.tool.model.ToolContext;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

import static com.xiaomizhou.dpsk.tool.model.ToolMetadata.RISK_NORMAL;

/**
 * 询问用户工具：当 Agent 遇到信息不足、需要用户澄清或补充时调用。
 * <p>
 * 执行流程：
 * <ol>
 *   <li>向当前会话推送 {@link WsMsgType#TOOL_ASK} 事件（含问题与唯一 requestId）；</li>
 *   <li>阻塞等待用户通过前端输入答案（默认 60s 超时）；</li>
 *   <li>将答案返回给 LLM，Agent 据此继续运行。</li>
 * </ol>
 * 每次调用生成独立 requestId，天然支持多轮问答。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ToolMeta(value = "向用户提问，当信息不足需要用户澄清或补充时使用", level = RISK_NORMAL,
        category = ToolCategory.BUILD_IN, tags = {"人机交互", "询问", "澄清"})
public class AskUserTool {

    private final ToolAskManager askManager;

    private final ConversationDao conversationDao;

    @Tool(name = "ask_user", value = "向用户提出一个问题并等待用户回答。当您缺少必要信息、需要用户确认、澄清或提供额外内容时调用。调用后请耐心等待用户输入，不要重复提问。")
    public String askUser(
            @P(description = "向用户提出的问题，应清晰具体，说明需要用户提供什么信息", required = true) String question,
            ToolContext context) {
        String conversationCode = context == null ? null : context.getConversationCode();
        // 诊断日志：确认 ToolContext 是否被注入以及会话编码是否为空
        log.info("ask_user invoked: context={}, conversationCode='{}', agentCode='{}', userCode='{}'",
                context == null ? "null" : "injected",
                conversationCode,
                context == null ? null : context.getAgentCode(),
                context == null ? null : context.getUserCode());
        String requestId = askManager.requestAsk();

        // 注册上下文，供提交答案时写用户消息留痕
        if (context != null) {
            Integer conversationType = null;
            try {
                Conversation conv = conversationDao.getOneByCode(context.getConversationCode());
                conversationType = conv == null ? null : conv.getConversationType();
            } catch (Exception e) {
                log.warn("Failed to load conversation type for code={}", context.getConversationCode(), e);
            }
            askManager.registerContext(requestId, AskContext.builder()
                    .conversationCode(context.getConversationCode())
                    .userCode(context.getUserCode())
                    .agentCode(context.getAgentCode())
                    .conversationType(conversationType)
                    .build());
        }

        // 1. WS 通知前端（只发当前会话连接，天然限定在本次会话）
        try {
            WsUtils.send(new WsMessage(WsMsgType.TOOL_ASK,
                    new ToolAskPayload(requestId, conversationCode, question, ToolAskManager.DEFAULT_TIMEOUT_SECONDS)));
        } catch (Exception e) {
            log.warn("Failed to send tool_ask event, requestId={}", requestId, e);
        }

        // 2. 阻塞等待用户输入（60s 超时，需小于 LocalToolExecutor 的 120s 超时）
        try {
            String answer = askManager.waitForAnswer(requestId, conversationCode,
                    ToolAskManager.DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (ToolAskManager.CANCEL_MARKER.equals(answer)) {
                log.info("ask_user cancelled by user, requestId={}", requestId);
                return "用户已取消本次提问，未提供答案。请根据已有信息继续，或调整策略。";
            }
            if (answer == null) {
                log.warn("ask_user timed out, requestId={}", requestId);
                return "用户未在 " + ToolAskManager.DEFAULT_TIMEOUT_SECONDS + " 秒内答复，未获得答案。请根据已有信息继续，必要时可换一种方式提问或推进其他步骤。";
            }
            log.info("ask_user answered, requestId={}", requestId);
            return "用户答复: " + answer;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "等待用户答复被中断，未获得答案。请根据已有信息继续。";
        }
    }
}

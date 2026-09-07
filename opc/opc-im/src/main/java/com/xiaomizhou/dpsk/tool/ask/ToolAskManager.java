package com.xiaomizhou.dpsk.tool.ask;

import com.xiaomizhou.dpsk.constant.ConversationType;
import com.xiaomizhou.dpsk.db.ChatMessageComponent;
import com.xiaomizhou.dpsk.db.dto.ChatMsgDto;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 工具询问用户管理器。
 * <p>
 * 为 {@code ask_user} 工具提供「阻塞等待用户输入」能力：
 * <ol>
 *   <li>工具方法先调用 {@link #requestAsk()} 生成唯一 requestId；</li>
 *   <li>再调用 {@link #waitForAnswer(String, String, long, TimeUnit)} 阻塞等待用户输入；</li>
 *   <li>前端通过 HTTP 接口调用 {@link #submitAnswer(String, String)}，把答案放入队列唤醒等待线程；</li>
 *   <li>Agent 取消时调用 {@link #cancelByConversation(String)} 清理该会话所有待答复请求。</li>
 * </ol>
 * <p>
 * 每次提问的 requestId 都不同，天然支持多轮问答。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolAskManager {

    /**
     * 默认等待超时（秒），需小于 LocalToolExecutor 的 120s 超时
     */
    public static final long DEFAULT_TIMEOUT_SECONDS = 60;

    /**
     * 取消标记：waitForAnswer 返回它时，工具应识别为用户已取消
     */
    public static final String CANCEL_MARKER = "__ASK_CANCELLED__";

    private final ChatMessageComponent chatMessageComponent;

    private final ConcurrentMap<String, BlockingQueue<String>> pending = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AskContext> requestContext = new ConcurrentHashMap<>();

    /**
     * 非 Spring 类（如 ImAgentCallback）访问的静态单例
     */
    private static ToolAskManager instance;

    @PostConstruct
    public void init() {
        instance = this;
    }

    public static ToolAskManager instance() {
        return instance;
    }

    /**
     * 发起一次提问，返回唯一 requestId。工具方法随后阻塞等待。
     */
    public String requestAsk() {
        String requestId = UUID.randomUUID().toString();
        pending.put(requestId, new LinkedBlockingQueue<>(1));
        return requestId;
    }

    /**
     * 注册一次提问的上下文，供提交答案时写用户消息留痕使用。
     */
    public void registerContext(String requestId, AskContext context) {
        if (requestId != null && context != null) {
            requestContext.put(requestId, context);
        }
    }

    /**
     * 工具方法内阻塞等待用户答案。
     *
     * @return 用户答案；超时未答复返回 null；用户取消返回 {@link #CANCEL_MARKER}
     */
    public String waitForAnswer(String requestId, String conversationCode, long timeout, TimeUnit unit) throws InterruptedException {
        BlockingQueue<String> queue = pending.get(requestId);
        if (queue == null) {
            return null;
        }
        return queue.poll(timeout, unit);
    }

    /**
     * 前端通过 HTTP 接口提交答案：
     * 1. 唤醒对应等待线程；
     * 2. 将答案作为一条「用户消息」写入消息表留痕。
     *
     * @return false 表示该 requestId 不存在或已过期
     */
    public boolean submitAnswer(String requestId, String answer) {
        BlockingQueue<String> queue = pending.get(requestId);
        if (queue == null) {
            log.warn("submitAnswer: no pending request for requestId={}", requestId);
            return false;
        }
        String content = answer == null ? "" : answer;
        queue.offer(content);

        // 用户消息留痕：工具结果是工具结果，用户消息是用户消息
        AskContext ctx = requestContext.get(requestId);
        if (ctx != null) {
            try {
                // 打开有问题，会导致llm tool 跟 result消息不连续导致api调用失败，暂时注释，后面想办法优化吧
//                saveUserMessage(ctx, content);
            } catch (Exception e) {
                log.error("Failed to save user message for ask requestId={}", requestId, e);
            }
        } else {
            log.warn("No ask context registered for requestId={}, skip saving user message", requestId);
        }
        return true;
    }

    /**
     * 将用户答案写入消息表（以用户身份发送的用户消息）。
     */
    private void saveUserMessage(AskContext ctx, String answer) {
        if (ctx == null || StringUtils.isAnyBlank(ctx.getConversationCode(), ctx.getUserCode(), ctx.getAgentCode())) {
            log.warn("Invalid ask context, skip saving user message: {}", ctx);
            return;
        }
        String userCode = ctx.getUserCode();
        String agentCode = ctx.getAgentCode();
        String conversationCode = ctx.getConversationCode();
        Integer conversationType = ctx.getConversationType() == null ? ConversationType.SINGLE.getCode() : ctx.getConversationType();

        ChatMsgDto dto = ChatMsgDto.builder()
                .sendId(userCode)
                .targetId(agentCode)
                .conversationCode(conversationCode)
                .conversationType(conversationType)
                .message(answer)
                .messageType("USER")
                .taskId(ctx.getTaskId())
                .build();

        if (ConversationType.GROUP.getCode().equals(conversationType)) {
            chatMessageComponent.newGroupChatMsg(userCode, dto, null, "");
        } else if (ConversationType.WORKFLOW.getCode().equals(conversationType)) {
            chatMessageComponent.newWorkflowMsg(userCode, dto, null, "");
        } else {
            chatMessageComponent.newSingleChatMsg(userCode, dto, null, "");
        }
        log.info("Saved user message for ask request, conversation={}, user={}", conversationCode, userCode);
    }

    /**
     * 取消指定会话下所有待答复的提问（Agent 取消/被打断时调用），
     * 让阻塞中的 ask_user 工具尽快返回而不是一直等到超时。
     */
    public void cancelByConversation(String conversationCode) {
        if (conversationCode == null) {
            return;
        }
        requestContext.forEach((requestId, ctx) -> {
            if (conversationCode.equals(ctx.getConversationCode())) {
                removeRequest(requestId);
                log.info("cancelled ask request {} for conversation {}", requestId, conversationCode);
            }
        });
    }

    /**
     * 取消指定的单个提问请求（前端主动取消），唤醒等待线程。
     */
    public void cancelByRequest(String requestId) {
        if (requestId == null) {
            return;
        }
        removeRequest(requestId);
        log.info("cancelled ask request {} by request", requestId);
    }

    private void removeRequest(String requestId) {
        BlockingQueue<String> queue = pending.remove(requestId);
        requestContext.remove(requestId);
        if (queue != null) {
            // 置入取消标记，唤醒等待线程
            queue.offer(CANCEL_MARKER);
        }
    }

    /**
     * 一次提问所需的上下文，用于提交答案时写用户消息留痕。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AskContext {
        /**
         * 会话编码
         */
        private String conversationCode;
        /**
         * 真实用户编码（消息发送者）
         */
        private String userCode;
        /**
         * Agent 编码（消息接收者，单聊 targetId）
         */
        private String agentCode;
        /**
         * 会话类型：0-单聊 1-群聊 2-专家团
         */
        private Integer conversationType;
        /**
         * 关联任务编码（可选）
         */
        private String taskId;
    }
}

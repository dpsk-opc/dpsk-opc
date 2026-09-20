package com.xiaomizhou.dpsk.tool.workspace;

import com.xiaomizhou.dpsk.agent.data.AgentDef;
import com.xiaomizhou.dpsk.agent.data.AgentDefProvider;
import com.xiaomizhou.dpsk.core.utils.WsUtils;
import com.xiaomizhou.dpsk.core.ws.WsMessage;
import com.xiaomizhou.dpsk.core.ws.WsMsgType;
import com.xiaomizhou.dpsk.core.ws.payload.PathAccessConfirmPayload;
import com.xiaomizhou.dpsk.tool.workspace.PathAccessConfirmer.ConfirmContext;
import com.xiaomizhou.dpsk.tool.workspace.PathAccessConfirmer.ConfirmResult;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 越界路径访问确认管理器（阻塞式）。
 * <p>
 * 复用 {@code ToolAskManager} 的阻塞 / 超时 / 取消模式：
 * <ol>
 *   <li>工具执行前发现需要确认 → 生成 requestId 并推送 {@link WsMsgType#PATH_ACCESS_CONFIRM}；</li>
 *   <li>阻塞等待用户答复；</li>
 *   <li>前端通过 HTTP 接口提交决定，唤醒等待线程；</li>
 *   <li><b>超时按拒绝处理</b>（安全优先）。</li>
 * </ol>
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/9/20
 */
@Slf4j
@Component
public class PathAccessConfirmManager implements PathAccessConfirmer {

    /**
     * 默认等待超时（秒），需小于 LocalToolExecutor 的 120s 超时
     */
    public static final long DEFAULT_TIMEOUT_SECONDS = 60;

    /**
     * 懒加载，避免构造期循环依赖（AgentDefProvider 的链路可能反向依赖本组件）。
     */
    private final ObjectProvider<AgentDefProvider> agentDefProviderProvider;

    private final ConcurrentMap<String, BlockingQueue<ConfirmResult>> pending = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConfirmContext> contexts = new ConcurrentHashMap<>();

    public PathAccessConfirmManager(ObjectProvider<AgentDefProvider> agentDefProviderProvider) {
        this.agentDefProviderProvider = agentDefProviderProvider;
    }

    private static PathAccessConfirmManager instance;

    @PostConstruct
    public void init() {
        instance = this;
    }

    public static PathAccessConfirmManager instance() {
        return instance;
    }

    @Override
    public ConfirmResult confirm(String title, List<PathAccess> accesses, String reason, ConfirmContext context) {
        if (context == null || StringUtils.isBlank(context.getConversationCode())) {
            log.warn("Path access confirm skipped: no conversation context, deny by default");
            return ConfirmResult.deny("缺少会话上下文，无法确认");
        }

        String requestId = UUID.randomUUID().toString();
        pending.put(requestId, new LinkedBlockingQueue<>(1));
        contexts.put(requestId, context);

        long timeout = context.getTimeoutSeconds() > 0 ? context.getTimeoutSeconds() : DEFAULT_TIMEOUT_SECONDS;

        // 1. 推送确认事件
        try {
            List<PathAccessConfirmPayload.PathItem> items = accesses.stream()
                    .map(a -> new PathAccessConfirmPayload.PathItem(a.getRawPath(),
                            a.getDirection() == null ? "READ" : a.getDirection().name()))
                    .collect(Collectors.toList());
            WsUtils.send(new WsMessage(WsMsgType.PATH_ACCESS_CONFIRM, new PathAccessConfirmPayload(
                    requestId, context.getConversationCode(), context.getAgentCode(),
                    resolveAgentName(context.getAgentCode()), title, items, reason, timeout)));
        } catch (Exception e) {
            log.warn("Failed to send path_access_confirm event, requestId={}", requestId, e);
            pending.remove(requestId);
            contexts.remove(requestId);
            return ConfirmResult.deny("确认事件推送失败");
        }

        // 2. 阻塞等待用户答复（超时按拒绝）
        try {
            BlockingQueue<ConfirmResult> queue = pending.get(requestId);
            ConfirmResult result = queue == null ? null : queue.poll(timeout, TimeUnit.SECONDS);
            if (result == null) {
                log.warn("Path access confirm timeout ({}s), deny by default, requestId={}", timeout, requestId);
                notifyTimeout(context.getConversationCode(), requestId);
                return ConfirmResult.deny("用户未在 " + timeout + " 秒内确认，已按拒绝处理");
            }
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ConfirmResult.deny("等待用户确认被中断");
        } finally {
            pending.remove(requestId);
            contexts.remove(requestId);
        }
    }

    /**
     * 提交用户的确认决定，唤醒等待线程。
     *
     * @param requestId 确认请求 ID
     * @param decision  决定：ONCE / SESSION_DIR / DENY
     * @return false 表示请求不存在或已过期
     */
    public boolean submitDecision(String requestId, String decision) {
        BlockingQueue<ConfirmResult> queue = pending.get(requestId);
        if (queue == null) {
            log.warn("submitDecision: no pending request for requestId={}", requestId);
            return false;
        }
        ConfirmResult.Decision d;
        try {
            d = ConfirmResult.Decision.valueOf(StringUtils.defaultIfBlank(decision, "DENY").trim().toUpperCase());
        } catch (Exception e) {
            log.warn("Invalid decision '{}', fallback to DENY", decision);
            d = ConfirmResult.Decision.DENY;
        }
        queue.offer(new ConfirmResult(d, null));
        return true;
    }

    /**
     * 用户主动取消（等同拒绝）。
     */
    public boolean cancelByRequest(String requestId) {
        BlockingQueue<ConfirmResult> queue = pending.remove(requestId);
        contexts.remove(requestId);
        if (queue != null) {
            queue.offer(ConfirmResult.deny("用户取消"));
            return true;
        }
        return false;
    }

    /**
     * 清理指定会话下所有待确认请求（Agent 取消时调用）。
     */
    public void cancelByConversation(String conversationCode) {
        if (StringUtils.isBlank(conversationCode)) {
            return;
        }
        contexts.forEach((requestId, ctx) -> {
            if (conversationCode.equals(ctx.getConversationCode())) {
                cancelByRequest(requestId);
            }
        });
    }

    /**
     * 查询 Agent 名称用于确认卡片展示，查不到时返回编码本身。
     */
    private String resolveAgentName(String agentCode) {
        if (StringUtils.isBlank(agentCode)) {
            return null;
        }
        try {
            AgentDefProvider provider = agentDefProviderProvider.getIfAvailable();
            if (provider == null) {
                return agentCode;
            }
            AgentDef def = provider.getByCode(agentCode);
            return def == null ? agentCode : def.getName();
        } catch (Exception e) {
            return agentCode;
        }
    }

    private void notifyTimeout(String conversationCode, String requestId) {
        try {
            WsUtils.send(new WsMessage(WsMsgType.PATH_ACCESS_CONFIRM_TIMEOUT,
                    java.util.Map.of("requestId", requestId, "conversationId", conversationCode)));
        } catch (Exception e) {
            log.warn("Failed to send path_access_confirm_timeout, requestId={}", requestId, e);
        }
    }
}

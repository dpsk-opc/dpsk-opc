package com.xiaomizhou.dpsk.tool;

import com.xiaomizhou.dpsk.tool.model.ToolCall;
import com.xiaomizhou.dpsk.tool.model.ToolMetadata;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具确认管理器。
 * <p>
 * 当前版本：危险操作仅打印 warn 日志，不实现完整的确认流程。
 * 后续可扩展为 Redis 存储确认请求，支持前端确认后继续执行。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/30
 */
@Slf4j
public class ToolConfirmationManager {

    /** 暂存待确认的调用（内存实现，后续替换为 Redis） */
    private final ConcurrentHashMap<String, ToolCall> pendingCalls = new ConcurrentHashMap<>();

    /**
     * 请求用户确认危险工具调用。
     *
     * @param call     工具调用
     * @param metadata 工具元数据
     * @return 确认请求ID
     */
    public String requestConfirmation(ToolCall call, ToolMetadata metadata) {
        String requestId = generateRequestId();
        pendingCalls.put(requestId, call);

        log.warn("⚠ DANGEROUS tool call requires confirmation! " +
                        "tool={}, riskLevel={}, requestId={}, params={}",
                metadata.getName(), metadata.getRiskLevel(), requestId, call.getParameters());

        return requestId;
    }

    /**
     * 确认工具调用。
     *
     * @param requestId 确认请求ID
     * @return 确认后的 ToolCall，若不存在则返回 null
     */
    public ToolCall confirm(String requestId) {
        ToolCall call = pendingCalls.remove(requestId);
        if (call != null) {
            call.setConfirmed(true);
            log.info("Tool call confirmed: requestId={}, tool={}", requestId, call.getName());
        }
        return call;
    }

    /**
     * 取消工具调用。
     *
     * @param requestId 确认请求ID
     */
    public void cancel(String requestId) {
        ToolCall removed = pendingCalls.remove(requestId);
        if (removed != null) {
            log.info("Tool call cancelled: requestId={}, tool={}", requestId, removed.getName());
        }
    }

    /**
     * 检查是否有待确认的调用。
     */
    public boolean hasPending(String requestId) {
        return pendingCalls.containsKey(requestId);
    }

    private String generateRequestId() {
        return "confirm_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}

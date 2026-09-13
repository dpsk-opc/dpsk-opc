package com.xiaomizhou.dpsk.core.ws;

import com.google.common.collect.Maps;
import com.xiaomizhou.dpsk.core.utils.WsUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

/**
 * 错误下发工具：把后端异常统一通过 WebSocket 推送给前端。
 * <p>
 * 设计要点：
 * 1. 使用 {@link Maps#newHashMap()} 而不是 {@code Map.of} —— {@code Map.of} 不允许 null value，
 * 一旦异常 message / agentCode 为 null 会直接抛 NPE，导致错误被吞掉、前端完全感知不到；
 * 2. 协议与前端既有实现保持一致（{@code type=error}，payload 仅 {@code code}/{@code message}，
 * 有 agent 时附带 {@code agent}），不新增字段，避免前端重新对接；
 * 3. 其余上下文（会话/流/任务、异常类型、根因、堆栈）只落日志，便于后端排查；
 * 4. 推送失败不能影响主流程，异常在此吞掉并记录日志。
 */
@Slf4j
public final class ErrorNotifier {

    private ErrorNotifier() {
    }

    /**
     * 便捷入口：不显式指定 message，由异常自动解析。
     */
    public static void send(String code, String conversationCode, String streamCode,
                            String taskId, String agentCode, Throwable error) {
        send(code, null, conversationCode, streamCode, taskId, agentCode, error);
    }

    /**
     * 构造并推送错误信息。
     *
     * @param code             错误码（如 EXECUTION_ERROR / DISPATCH_ERROR）
     * @param message          错误消息，为空时从异常根因解析
     * @param conversationCode 会话编码，便于前端把错误挂到对应会话
     * @param streamCode       流式编码
     * @param taskId           任务编码
     * @param agentCode        出错 Agent 编码
     * @param error            原始异常（可为 null）
     */
    public static void send(String code, String message, String conversationCode, String streamCode,
                            String taskId, String agentCode, Throwable error) {
        String errorMessage = resolveMessage(message, error);
        try {
            // payload 结构保持不变：code + message（有 agent 时补一个 agent），与前端既有解析一致
            Map<String, Object> payload = Maps.newHashMap();
            payload.put("code", code);
            payload.put("message", errorMessage);
            if (StringUtils.isNotBlank(agentCode)) {
                payload.put("agent", agentCode);
            }
            boolean sent = WsUtils.send(new WsMessage(WsMsgType.ERROR, payload));
            // 新增上下文只落日志：message 给前端，堆栈/根因留在服务端便于排查
            log.warn("Error pushed to frontend: code={}, conversation={}, stream={}, task={}, agent={}, sent={}, message={}",
                    code, conversationCode, streamCode, taskId, agentCode, sent, errorMessage, error);
        } catch (Exception e) {
            log.error("Failed to push error to frontend: code={}, conversation={}", code, conversationCode, e);
        }
    }

    /**
     * 解析错误消息：优先使用已有 message，其次取根因消息，最后兜底异常类名，保证不为空。
     */
    static String resolveMessage(String message, Throwable error) {
        if (StringUtils.isNotBlank(message)) {
            return message;
        }
        if (error == null) {
            return "未知错误";
        }
        Throwable root = rootCause(error);
        String rootMessage = root != null ? root.getMessage() : null;
        if (StringUtils.isNotBlank(rootMessage)) {
            return root.getClass().getSimpleName() + ": " + rootMessage;
        }
        return error.getClass().getName();
    }

    static Throwable rootCause(Throwable error) {
        Throwable cause = error;
        while (cause != null && cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}

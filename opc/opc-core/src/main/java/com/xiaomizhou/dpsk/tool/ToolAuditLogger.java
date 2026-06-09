package com.xiaomizhou.dpsk.tool;

import com.xiaomizhou.dpsk.tool.model.ToolAuditLog;
import com.xiaomizhou.dpsk.tool.model.ToolCall;
import com.xiaomizhou.dpsk.tool.model.ToolContext;
import com.xiaomizhou.dpsk.tool.model.ToolMetadata;
import com.xiaomizhou.dpsk.tool.repository.ToolAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.UUID;

/**
 * 工具调用审计日志记录器。
 * <p>
 * 异步写入，不阻塞主流程。会对敏感参数做脱敏处理。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/30
 */
@Slf4j
@RequiredArgsConstructor
public class ToolAuditLogger {

    private final ToolAuditLogRepository auditLogRepository;

    /** 敏感参数关键词 */
    private static final String[] SENSITIVE_KEYS = {"password", "secret", "token", "apiKey", "api_key", "key"};

    /**
     * 记录工具调用审计日志。
     *
     * @param call     工具调用
     * @param metadata 工具元数据
     * @param context  执行上下文
     * @param result   执行结果
     * @param status   执行状态
     * @param executionTimeMs 耗时
     * @param errorMessage 错误信息
     */
    public void log(ToolCall call, ToolMetadata metadata, ToolContext context,
                    String result, String status, long executionTimeMs, String errorMessage) {
        try {
            ToolAuditLog auditLog = ToolAuditLog.builder()
                    .code(generateCode())
                    .toolCode(metadata.getCode())
                    .toolName(metadata.getName())
                    .agentCode(context.getAgentCode())
                    .userCode(context.getUserCode())
                    .conversationCode(context.getConversationCode())
                    .requestParams(sanitizeParams(call))
                    .responseSummary(truncate(result, 500))
                    .status(status)
                    .riskLevel(metadata.getRiskLevel())
                    .executionTimeMs((int) executionTimeMs)
                    .errorMessage(errorMessage)
                    .traceId(context.getTraceId())
                    .createTime(Instant.now())
                    .build();

            auditLogRepository.save(auditLog);
            log.debug("Audit log saved: tool={}, status={}, traceId={}", 
                    metadata.getName(), status, context.getTraceId());
        } catch (Exception e) {
            // 审计日志写入失败不应影响主流程
            log.warn("Failed to save tool audit log for tool: {}", metadata.getName(), e);
        }
    }

    /**
     * 参数脱敏：标记敏感字段不记录原值。
     */
    private String sanitizeParams(ToolCall call) {
        if (call.getParameters() == null || call.getParameters().isEmpty()) {
            return "{}";
        }

        try {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (var entry : call.getParameters().entrySet()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append("\"").append(entry.getKey()).append("\": ");
                if (isSensitive(entry.getKey())) {
                    sb.append("\"***\"");
                } else {
                    String value = entry.getValue() != null ? entry.getValue().toString() : "null";
                    sb.append("\"").append(truncate(value, 200)).append("\"");
                }
            }
            sb.append("}");
            return sb.toString();
        } catch (Exception e) {
            return "{\"error\": \"serialization failed\"}";
        }
    }

    private boolean isSensitive(String key) {
        if (key == null) return false;
        String lower = key.toLowerCase();
        for (String sk : SENSITIVE_KEYS) {
            if (lower.contains(sk)) {
                return true;
            }
        }
        return false;
    }

    private String truncate(String value, int maxLen) {
        if (value == null) return null;
        if (value.length() <= maxLen) return value;
        return value.substring(0, maxLen) + "...(truncated)";
    }

    private String generateCode() {
        return "audit_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }
}

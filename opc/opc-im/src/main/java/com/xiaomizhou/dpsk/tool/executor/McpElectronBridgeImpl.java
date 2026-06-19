package com.xiaomizhou.dpsk.tool.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaomizhou.dpsk.core.ws.WsMessage;
import com.xiaomizhou.dpsk.core.ws.WsMsgType;
import com.xiaomizhou.dpsk.core.ws.payload.McpCallPayload;
import com.xiaomizhou.dpsk.tool.model.McpElectronRequest;
import com.xiaomizhou.dpsk.tool.model.McpElectronResult;
import com.xiaomizhou.dpsk.core.utils.WsUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * MCP Electron 桥接实现，基于 WebSocket 与前端 Electron 通信。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/16
 */
@Component
@Slf4j
public class McpElectronBridgeImpl implements McpElectronBridge {

    private static final long TIMEOUT_MS = 30_000;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 等待中的 Future，key=callId */
    private static final Map<String, CompletableFuture<McpElectronResult>> PENDING = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("McpElectronBridgeImpl initialized");
    }

    @Override
    public McpElectronResult call(McpElectronRequest request) throws Exception {
        String callId = request.getCallId();
        CompletableFuture<McpElectronResult> future = new CompletableFuture<>();
        PENDING.put(callId, future);

        try {
            // 构建 WebSocket 消息 payload
            McpCallPayload payload = new McpCallPayload();
            payload.setCallId(callId);
            payload.setCommand(request.getCommand());
            payload.setArgs(request.getArgs());
            payload.setEnvVars(request.getEnvVars());

            // 根据 toolName 判断 method: null → tools/list, 非 null → tools/call
            if (request.getToolName() == null) {
                payload.setMethod("tools/list");
            } else {
                payload.setMethod("tools/call");
                payload.setToolName(request.getToolName());
            }

            // arguments 是 JSON 字符串，需要解析为 Object
            if (request.getArguments() != null) {
                try {
                    payload.setArguments(MAPPER.readTree(request.getArguments()));
                } catch (Exception e) {
                    payload.setArguments(request.getArguments());
                }
            }

            // context 是 JSON 字符串，需要解析为 Object
            if (request.getContext() != null) {
                try {
                    payload.setContext(MAPPER.readTree(request.getContext()));
                } catch (Exception e) {
                    payload.setContext(request.getContext());
                }
            }



            WsMessage msg = new WsMessage(WsMsgType.MCP_CALL, payload);

            try {
                if (WsUtils.channel == null) {
                    throw new IllegalStateException("WebSocket channel not available");
                }
                WsUtils.channel.to(msg);
            } catch (Exception e) {
                PENDING.remove(callId);
                throw new RuntimeException("Failed to send MCP call via WebSocket: " + e.getMessage(), e);
            }

            // 同步等待结果
            McpElectronResult result = future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            log.info("MCP Electron call completed: callId={}, success={}", callId, result.isSuccess());
            return result;
        } catch (TimeoutException e) {
            PENDING.remove(callId);
            throw new RuntimeException("MCP Electron call timed out after " + TIMEOUT_MS + "ms: callId=" + callId);
        } catch (Exception e) {
            PENDING.remove(callId);
            throw e;
        }
    }

    /**
     * 完成等待中的 MCP 调用（由 ChatWsChannel 收到 mcp_call_result 时调用）。
     */
    public static void complete(String callId, boolean success, String result, String error) {
        CompletableFuture<McpElectronResult> future = PENDING.remove(callId);
        if (future != null) {
            McpElectronResult mcpResult = McpElectronResult.builder()
                    .success(success)
                    .result(result)
                    .error(error)
                    .build();
            future.complete(mcpResult);
        } else {
            log.warn("No pending future found for callId: {}", callId);
        }
    }
}

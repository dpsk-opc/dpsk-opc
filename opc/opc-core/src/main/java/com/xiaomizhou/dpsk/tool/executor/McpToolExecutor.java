package com.xiaomizhou.dpsk.tool.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaomizhou.dpsk.tool.ToolExecutor;
import com.xiaomizhou.dpsk.tool.model.*;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * MCP 工具执行器，通过 MCP 协议调用远程工具服务。
 * <p>
 * 根据 runtimeEnv 分发：
 * - backend(2): 后端 spawn 子进程，通过 JSON-RPC tools/call 执行
 * - electron(1): 通过 McpElectronBridge 转发给前端 Electron 执行
 * <p>
 * 运行时依赖通过 Spring 注入：McpBindingRepository 和 McpElectronBridge。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/30
 */
@Slf4j
public class McpToolExecutor implements ToolExecutor {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long TIMEOUT_MS = 30_000;

    /**
     * 上下文参数 key，这些 key 不应发送给 MCP 工具，而是单独走 context 字段
     */
    private static final Set<String> CONTEXT_KEYS = Set.of(
            "agentCode", "userCode", "conversationCode", "traceId"
    );

    private final McpBindingRepository bindingRepository;
    private final McpElectronBridge electronBridge;

    public McpToolExecutor() {
        this.bindingRepository = null;
        this.electronBridge = null;
    }

    public McpToolExecutor(McpBindingRepository bindingRepository, McpElectronBridge electronBridge) {
        this.bindingRepository = bindingRepository;
        this.electronBridge = electronBridge;
    }

    @Override
    public ToolResult execute(ToolCall call, ToolContext context, ToolMetadata metadata) {
        // 1. 从 sourceRef 解析 bindingCode + toolName
        // sourceRef 格式: "{bindingCode}:{toolName}"
        String sourceRef = (String) call.getParameters().get("__sourceRef__");
        if (sourceRef == null || !sourceRef.contains(":")) {
            return ToolResult.fail(ToolExecutionResult.ERROR_BEAN_OR_METHOD_NOT_FOUND,
                    "MCP 工具 sourceRef 非法，期望 {bindingCode}:{toolName}，实际: " + sourceRef);
        }

        String[] parts = sourceRef.split(":", 2);
        String bindingCode = parts[0];
        String toolName = parts[1];

        log.info("Executing MCP tool: bindingCode={}, toolName={}", bindingCode, toolName);

        // 2. 查询绑定信息
        if (bindingRepository == null) {
            return ToolResult.fail(ToolExecutionResult.ERROR_EXECUTION_ERROR, "McpBindingRepository 不可用");
        }

        McpBindingInfo binding = bindingRepository.findByCode(bindingCode);
        if (binding == null) {
            return ToolResult.fail(ToolExecutionResult.ERROR_BEAN_OR_METHOD_NOT_FOUND,
                    "MCP 绑定不存在: " + bindingCode);
        }

        // 3. 分离上下文参数与工具参数
        Map<String, Object> allParams = call.getParameters();
        // 移除内部字段
        allParams.remove("__sourceRef__");

        Map<String, Object> toolArgs = new LinkedHashMap<>();
        Map<String, Object> ctx = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : allParams.entrySet()) {
            if (CONTEXT_KEYS.contains(entry.getKey())) {
                ctx.put(entry.getKey(), entry.getValue());
            } else {
                toolArgs.put(entry.getKey(), entry.getValue());
            }
        }

        // 4. 根据 runtimeEnv 分发执行
        // runtimeEnv: 1-electron, 2-backend
        try {
            String result;
            if (binding.getRuntimeEnv() != null && binding.getRuntimeEnv() == 1) {
                result = executeViaElectron(binding, toolName, toolArgs, ctx);
            } else {
                String jsonRpcRequest = buildJsonRpcRequest(toolName, toolArgs);
                log.debug("JSON-RPC request: {}", jsonRpcRequest);
                result = executeViaBackend(binding, jsonRpcRequest);
            }
            return ToolResult.builder()
                    .success(true)
                    .data(result)
                    .text(result)
                    .build();
        } catch (Exception e) {
            log.error("MCP tool execution failed: {}", call.getName(), e);
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("timed out")) {
                return ToolResult.fail(ToolExecutionResult.ERROR_TIMEOUT, "MCP 工具执行超时: " + call.getName(), e);
            }
            return ToolResult.fail(ToolExecutionResult.ERROR_EXECUTION_ERROR,
                    "MCP 工具执行失败: " + call.getName(), e);
        }
    }

    /**
     * 后端 spawn 子进程执行 JSON-RPC。
     */
    private String executeViaBackend(McpBindingInfo binding, String jsonRpcRequest) throws Exception {
        ProcessBuilder pb = new ProcessBuilder();
        pb.command(buildCommandArray(binding.getCommand(), binding.getArgs()));
        pb.redirectErrorStream(true);

        // 设置环境变量
        if (binding.getEnvVars() != null) {
            pb.environment().putAll(binding.getEnvVars());
        }

        Process process = pb.start();

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
             BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {

            // 发送 JSON-RPC 请求
            writer.write(jsonRpcRequest);
            writer.newLine();
            writer.flush();

            // 读取响应
            StringBuilder responseBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                responseBuilder.append(line);
            }

            boolean finished = process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("MCP tool execution timed out after " + TIMEOUT_MS + "ms");
            }

            String response = responseBuilder.toString();
            log.debug("MCP response: {}", response);

            // 解析 JSON-RPC 响应
            return parseJsonRpcResponse(response, binding.getToolName());
        }
    }

    /**
     * 通过 Electron 桥接执行。
     * <p>
     * arguments 只包含 MCP 工具自身参数，context 单独传递，前端无需区分哪些是上下文。
     */
    private String executeViaElectron(McpBindingInfo binding, String toolName,
                                      Map<String, Object> toolArgs, Map<String, Object> ctx) throws Exception {
        if (electronBridge == null) {
            throw new IllegalStateException("Electron bridge not available");
        }

        McpElectronRequest request = McpElectronRequest.builder()
                .callId(binding.getCode() + "_" + System.currentTimeMillis())
                .command(binding.getCommand())
                .args(binding.getArgs())
                .envVars(binding.getEnvVars())
                .toolName(toolName)
                .arguments(MAPPER.writeValueAsString(toolArgs))
                .context(ctx.isEmpty() ? null : MAPPER.writeValueAsString(ctx))
                .build();

        McpElectronResult result = electronBridge.call(request);
        if (!result.isSuccess()) {
            throw new RuntimeException("MCP Electron execution failed: " + result.getError());
        }
        return result.getResult();
    }

    private String buildJsonRpcRequest(String toolName, Map<String, Object> arguments) throws Exception {
        ObjectNode request = MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", 1);
        request.put("method", "tools/call");
        ObjectNode paramsNode = MAPPER.createObjectNode();
        paramsNode.put("name", toolName);
        paramsNode.set("arguments", MAPPER.valueToTree(arguments));
        request.set("params", paramsNode);
        return MAPPER.writeValueAsString(request);
    }

    private String[] buildCommandArray(String command, String[] args) {
        String[] cmd = new String[args.length + 1];
        cmd[0] = command;
        System.arraycopy(args, 0, cmd, 1, args.length);
        return cmd;
    }

    private String parseJsonRpcResponse(String response, String toolName) throws Exception {
        JsonNode root = MAPPER.readTree(response);
        if (root.has("error")) {
            String errorMsg = root.path("error").path("message").asText("Unknown MCP error");
            throw new RuntimeException("MCP tool '" + toolName + "' error: " + errorMsg);
        }
        JsonNode result = root.path("result");
        if (result.has("content")) {
            return result.path("content").toString();
        }
        return result.toString();
    }

    // ---- 内部模型 ----

    /**
     * MCP 绑定信息，由 im 模块实现注入。
     */
    public interface McpBindingRepository {
        McpBindingInfo findByCode(String bindingCode);
    }

    /**
     * MCP 绑定信息。
     */
    public static class McpBindingInfo {
        private String code;
        private String command;
        private String[] args;
        private Map<String, String> envVars;
        private Integer runtimeEnv;
        private String toolName;

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getCommand() { return command; }
        public void setCommand(String command) { this.command = command; }
        public String[] getArgs() { return args; }
        public void setArgs(String[] args) { this.args = args; }
        public Map<String, String> getEnvVars() { return envVars; }
        public void setEnvVars(Map<String, String> envVars) { this.envVars = envVars; }
        public Integer getRuntimeEnv() { return runtimeEnv; }
        public void setRuntimeEnv(Integer runtimeEnv) { this.runtimeEnv = runtimeEnv; }
        public String getToolName() { return toolName; }
        public void setToolName(String toolName) { this.toolName = toolName; }
    }
}

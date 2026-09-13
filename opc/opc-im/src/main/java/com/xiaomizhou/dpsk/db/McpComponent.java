package com.xiaomizhou.dpsk.db;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaomizhou.dpsk.core.exceptions.BusinessException;
import com.xiaomizhou.dpsk.db.dao.AgentMcpBindingDao;
import com.xiaomizhou.dpsk.db.dao.McpTemplateDao;
import com.xiaomizhou.dpsk.db.dao.ToolDao;
import com.xiaomizhou.dpsk.db.dto.*;
import com.xiaomizhou.dpsk.db.model.AgentMcpBindingDO;
import com.xiaomizhou.dpsk.db.model.McpTemplateDO;
import com.xiaomizhou.dpsk.db.model.ToolDO;
import com.xiaomizhou.dpsk.tool.SourceType;
import com.xiaomizhou.dpsk.tool.executor.McpElectronBridge;
import com.xiaomizhou.dpsk.tool.model.McpElectronRequest;
import com.xiaomizhou.dpsk.tool.model.McpElectronResult;
import com.xiaomizhou.dpsk.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * MCP 管理业务组件。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/16
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class McpComponent {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long DISCOVER_TIMEOUT_MS = 15_000;

    private final McpTemplateDao mcpTemplateDao;
    private final AgentMcpBindingDao agentMcpBindingDao;
    private final ToolDao toolDao;
    private final McpElectronBridge electronBridge;

    // ---- 工具发现 ----

    /**
     * 发现 MCP 工具：spawn 进程发送 tools/list JSON-RPC，返回工具列表。
     * <p>
     * 根据命令判断运行时环境：
     * - npx/node → 通过 McpElectronBridge 转发给前端 Electron 执行 tools/list
     * - python/go/docker/uvx → 后端直接 spawn 子进程执行 tools/list
     * - 命令不可用 → runtimeAvailable=false, runtimeEnv=none
     */
    public McpDiscoverVO discoverTools(McpDiscoverRequest req) {
        String command = req.getCommand();
        if (StringUtils.isBlank(command)) {
            return McpDiscoverVO.builder()
                    .runtimeAvailable(false)
                    .runtimeEnv("none")
                    .tools(List.of())
                    .build();
        }

        // 1. 检查命令是否在 PATH 中可找到
        boolean available = isCommandOnPath(command);
        if (!available) {
            return McpDiscoverVO.builder()
                    .runtimeAvailable(false)
                    .runtimeEnv("none")
                    .tools(List.of())
                    .build();
        }

        // 2. 根据命令类型确定 runtimeEnv
        String runtimeEnv = resolveRuntimeEnv(command);
        if ("none".equals(runtimeEnv)) {
            return McpDiscoverVO.builder()
                    .runtimeAvailable(false)
                    .runtimeEnv("none")
                    .tools(List.of())
                    .build();
        }

        // 3. 实际执行 tools/list JSON-RPC
        try {
            List<McpToolItem> tools;
            if ("electron".equals(runtimeEnv)) {
                tools = discoverViaElectron(command, req.getArgs(), req.getEnvVars());
            } else {
                tools = discoverViaBackend(command, req.getArgs(), req.getEnvVars());
            }

            return McpDiscoverVO.builder()
                    .runtimeAvailable(true)
                    .runtimeEnv(runtimeEnv)
                    .tools(tools)
                    .build();
        } catch (Exception e) {
            log.warn("MCP tools/list failed: command={}, error={}", command, e.getMessage());
            return McpDiscoverVO.builder()
                    .runtimeAvailable(false)
                    .runtimeEnv(runtimeEnv)
                    .tools(List.of())
                    .build();
        }
    }

    /**
     * 检查命令是否在系统 PATH 中可找到。
     */
    private boolean isCommandOnPath(String command) {
        try {
            ProcessBuilder pb;
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                pb = new ProcessBuilder("where", command);
            } else {
                pb = new ProcessBuilder("which", command);
            }
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            log.debug("Command '{}' not found on PATH: {}", command, e.getMessage());
            return false;
        }
    }

    /**
     * 通过 Electron 桥接执行 tools/list。
     * 前端 Electron 直接返回 tools 数组的 JSON，不走标准 JSON-RPC 格式。
     */
    private List<McpToolItem> discoverViaElectron(String command, String[] args, String envVars) throws Exception {
        String callId = "discover_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        McpElectronRequest request = McpElectronRequest.builder()
                .callId(callId)
                .command(command)
                .args(args != null ? args : new String[0])
                .envVars(parseEnvVars(envVars))
                .toolName(null)  // null 表示 tools/list
                .arguments(null)
                .context(null)    // tools/list 不需要上下文
                .build();

        McpElectronResult result = electronBridge.call(request);
        if (!result.isSuccess()) {
            throw new RuntimeException("Electron tools/list failed: " + result.getError());
        }

        // Electron 返回的是 tools 数组的 JSON，直接解析为 McpToolItem 列表
        return parseToolsArray(result.getResult());
    }

    /**
     * 解析 tools 数组 JSON（Electron 直接返回的格式）。
     */
    private List<McpToolItem> parseToolsArray(String json) throws Exception {
        if (StringUtils.isBlank(json)) {
            return List.of();
        }
        JsonNode root = MAPPER.readTree(json);
        // 防御：数据库 JSON 列可能被多重序列化，递归解开所有文本层
        while (root.isTextual()) {
            String inner = root.asText();
            if (StringUtils.isBlank(inner)) {
                return List.of();
            }
            root = MAPPER.readTree(inner);
        }
        if (!root.isArray()) {
            log.warn("Expected JSON array for tools, got node type: {}, raw: {}", root.getNodeType(), json);
            return List.of();
        }
        List<McpToolItem> tools = new ArrayList<>();
        for (JsonNode node : root) {
            McpToolItem item = new McpToolItem();
            item.setName(node.path("name").asText(null));
            item.setDescription(node.path("description").asText(null));
            JsonNode schema = node.path("inputSchema");
            if (!schema.isMissingNode() && !schema.isNull()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> schemaMap = MAPPER.convertValue(schema, Map.class);
                item.setInputSchema(schemaMap);
            }
            tools.add(item);
        }
        return tools;
    }

    /**
     * 后端 spawn 子进程执行 tools/list JSON-RPC。
     */
    private List<McpToolItem> discoverViaBackend(String command, String[] args, String envVars) throws Exception {
        String[] cmdArray = buildCmdArray(command, args);

        ProcessBuilder pb = new ProcessBuilder(cmdArray);
        pb.redirectErrorStream(true);

        Map<String, String> env = parseEnvVars(envVars);
        if (!env.isEmpty()) {
            pb.environment().putAll(env);
        }

        Process process = pb.start();

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
             BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {

            // 发送 tools/list JSON-RPC 请求
            String request = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}";
            writer.write(request);
            writer.newLine();
            writer.flush();

            // 读取响应
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }

            boolean finished = process.waitFor(DISCOVER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("MCP tools/list timed out after " + DISCOVER_TIMEOUT_MS + "ms");
            }

            String response = sb.toString();
            log.debug("MCP tools/list response: {}", response);

            return parseToolsListResponse(response);
        }
    }

    /**
     * 解析 tools/list JSON-RPC 响应，提取工具列表。
     */
    private List<McpToolItem> parseToolsListResponse(String response) throws Exception {
        if (StringUtils.isBlank(response)) {
            return List.of();
        }

        JsonNode root = MAPPER.readTree(response);

        // 检查 JSON-RPC error
        if (root.has("error")) {
            String errorMsg = root.path("error").path("message").asText("Unknown MCP error");
            throw new RuntimeException("MCP tools/list error: " + errorMsg);
        }

        // 解析 result.tools 数组
        JsonNode toolsNode = root.path("result").path("tools");
        if (!toolsNode.isArray()) {
            return List.of();
        }

        List<McpToolItem> tools = new ArrayList<>();
        for (JsonNode node : toolsNode) {
            McpToolItem item = new McpToolItem();
            item.setName(node.path("name").asText(null));
            item.setDescription(node.path("description").asText(null));

            JsonNode schema = node.path("inputSchema");
            if (!schema.isMissingNode() && !schema.isNull()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> schemaMap = MAPPER.convertValue(schema, Map.class);
                item.setInputSchema(schemaMap);
            }
            tools.add(item);
        }

        return tools;
    }

    /**
     * 解析 envVars 字符串（多行 KEY=VALUE）为 Map。
     */
    private Map<String, String> parseEnvVars(String envVars) {
        if (StringUtils.isBlank(envVars)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String line : envVars.split("\\n")) {
            line = line.trim();
            if (line.isEmpty() || !line.contains("=")) {
                continue;
            }
            int idx = line.indexOf('=');
            String key = line.substring(0, idx).trim();
            String value = line.substring(idx + 1).trim();
            if (!key.isEmpty()) {
                result.put(key, value);
            }
        }
        return result;
    }

    /**
     * 构建命令数组。
     */
    private String[] buildCmdArray(String command, String[] args) {
        if (args == null || args.length == 0) {
            return new String[]{command};
        }
        String[] cmd = new String[args.length + 1];
        cmd[0] = command;
        System.arraycopy(args, 0, cmd, 1, args.length);
        return cmd;
    }

    private String resolveRuntimeEnv(String command) {
        if (command == null) return "none";
        return switch (command.toLowerCase()) {
            case "npx", "node" -> "electron";
            case "python", "go", "docker", "uvx" -> "backend";
            default -> "none";
        };
    }

    // ---- 模板 CRUD ----

    public IPage<McpTemplateVO> listTemplates(int pageNo, int pageSize, String keyword) {
        Page<McpTemplateDO> page = new Page<>(pageNo, pageSize);

        List<McpTemplateDO> list;
        long total;

        if (StringUtils.isNotBlank(keyword)) {
            list = mcpTemplateDao.searchByKeyword(keyword);
            total = list.size();
            // 手动分页
            int from = (pageNo - 1) * pageSize;
            int to = Math.min(from + pageSize, list.size());
            list = from < list.size() ? list.subList(from, to) : List.of();
        } else {
            list = mcpTemplateDao.lambdaQuery()
                    .eq(McpTemplateDO::getIsDeleted, 0)
                    .orderByDesc(McpTemplateDO::getCreateTime)
                    .page(page)
                    .getRecords();
            total = page.getTotal();
        }

        List<McpTemplateVO> vos = list.stream().map(this::toTemplateVO).collect(Collectors.toList());

        IPage<McpTemplateVO> result = new Page<>(pageNo, pageSize, total);
        result.setRecords(vos);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public String addTemplate(McpTemplateSaveRequest req) {
        if (StringUtils.isBlank(req.getName())) {
            throw BusinessException.paramError("模板名称不能为空");
        }
        if (StringUtils.isBlank(req.getCommand())) {
            throw BusinessException.paramError("启动命令不能为空");
        }

        String code = "mcp_tpl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        McpTemplateDO entity = new McpTemplateDO();
        entity.setCode(code);
        entity.setName(req.getName());
        entity.setDescription(StringUtils.defaultString(req.getDescription()));
        entity.setCommand(req.getCommand());
        entity.setArgs(req.getArgs() != null ? JsonUtils.toJson(req.getArgs()) : "[]");
        entity.setRuntimeEnv(resolveRuntimeEnvInt(req.getRuntimeEnv()));
        entity.setRuntimeAvailable(req.getRuntimeAvailable() != null && req.getRuntimeAvailable() ? 1 : 0);
        entity.setTools(req.getTools() != null ? JsonUtils.toJson(req.getTools()) : "[]");
        entity.setCreateTime(new Date());
        entity.setUpdateTime(new Date());
        entity.setIsDeleted(0);

        mcpTemplateDao.save(entity);
        log.info("MCP template created: code={}, name={}", code, req.getName());
        return code;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateTemplate(McpTemplateSaveRequest req) {
        if (StringUtils.isBlank(req.getId())) {
            throw BusinessException.paramError("模板 ID 不能为空");
        }

        McpTemplateDO entity = mcpTemplateDao.getByCode(req.getId());
        if (entity == null) {
            throw BusinessException.notFound("模板不存在: " + req.getId());
        }

        if (StringUtils.isNotBlank(req.getName())) {
            entity.setName(req.getName());
        }
        if (req.getDescription() != null) {
            entity.setDescription(req.getDescription());
        }
        if (StringUtils.isNotBlank(req.getCommand())) {
            entity.setCommand(req.getCommand());
        }
        if (req.getArgs() != null) {
            entity.setArgs(JsonUtils.toJson(req.getArgs()));
        }
        if (req.getRuntimeEnv() != null) {
            entity.setRuntimeEnv(resolveRuntimeEnvInt(req.getRuntimeEnv()));
        }
        if (req.getRuntimeAvailable() != null) {
            entity.setRuntimeAvailable(req.getRuntimeAvailable() ? 1 : 0);
        }
        if (req.getTools() != null) {
            entity.setTools(JsonUtils.toJson(req.getTools()));
        }
        entity.setUpdateTime(new Date());

        mcpTemplateDao.updateById(entity);
        log.info("MCP template updated: code={}", req.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteTemplate(String id) {
        if (StringUtils.isBlank(id)) {
            throw BusinessException.paramError("模板 ID 不能为空");
        }
        McpTemplateDO entity = mcpTemplateDao.getByCode(id);
        if (entity == null) {
            throw BusinessException.notFound("模板不存在: " + id);
        }

        // 级联物理删除关联的绑定和工具（模板没了，绑定也没意义了）
        List<AgentMcpBindingDO> bindings = agentMcpBindingDao.findByTemplateCode(id);
        for (AgentMcpBindingDO binding : bindings) {
            // 物理删除关联的 tool 记录
            List<ToolDO> tools = toolDao.findBySourceRefPrefix(binding.getCode());
            if (!tools.isEmpty()) {
                List<Long> toolIds = tools.stream().map(ToolDO::getId).collect(Collectors.toList());
                toolDao.hardDeleteByIds(toolIds);
            }
            agentMcpBindingDao.hardDeleteById(binding.getId());
        }
        if (!bindings.isEmpty()) {
            log.info("Cascade deleted {} bindings for deleted template: code={}", bindings.size(), id);
        }

        mcpTemplateDao.deleteByCode(id);
        log.info("MCP template deleted: code={}", id);
    }

    // ---- 绑定 CRUD ----

    public IPage<McpBindingVO> listBindings(int pageNo, int pageSize, String agentCode, Boolean enabled) {

        // 默认只查已绑定的（enabled=1），除非明确传了 enabled=false
        List<AgentMcpBindingDO> agents;
        if (enabled != null && !enabled) {
            agents = agentMcpBindingDao.findByAgentCode(agentCode);
        } else {
            agents = agentMcpBindingDao.findEnabledByAgentCode(agentCode);
        }

        List<McpBindingVO> vos = agents.stream().map(this::toBindingVO).collect(Collectors.toList());

        IPage<McpBindingVO> result = new Page<>(pageNo, pageSize, agents.size());
        result.setRecords(vos);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveBinding(McpBindingSaveRequest req, String headerAgentCode) {
        if (StringUtils.isBlank(req.getTemplateId())) {
            throw BusinessException.paramError("模板 ID 不能为空");
        }

        String agentCode = StringUtils.isNotBlank(req.getAgentCode()) ? req.getAgentCode() : headerAgentCode;
        if (StringUtils.isBlank(agentCode)) {
            throw BusinessException.paramError("Agent 编码不能为空");
        }

        // 校验模板存在
        McpTemplateDO template = mcpTemplateDao.getByCode(req.getTemplateId());
        if (template == null) {
            throw BusinessException.notFound("模板不存在: " + req.getTemplateId());
        }

        // 查找现有绑定（含之前解绑 enabled=0 的记录）
        AgentMcpBindingDO existing = agentMcpBindingDao.findByAgentAndTemplate(agentCode, req.getTemplateId());

        if (existing != null) {
            // 重新激活绑定
            existing.setEnabled(1);
            if (req.getEnvVars() != null) {
                existing.setEnvVars(req.getEnvVars());
            }
            existing.setUpdateTime(new Date());
            agentMcpBindingDao.updateById(existing);

            // 同步 t_tool 状态为启用
            syncToolStatus(existing.getCode(), true);
            log.info("MCP binding re-activated: agentCode={}, templateCode={}", agentCode, req.getTemplateId());
        } else {
            // 新增
            String bindingCode = "mcp_bind_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

            AgentMcpBindingDO binding = new AgentMcpBindingDO();
            binding.setCode(bindingCode);
            binding.setTemplateCode(req.getTemplateId());
            binding.setAgentCode(agentCode);
            binding.setEnabled(1);
            binding.setEnvVars(StringUtils.defaultString(req.getEnvVars()));
            binding.setStatus(0); // stopped
            binding.setToolsSnapshot(template.getTools());
            binding.setCreateTime(new Date());
            binding.setUpdateTime(new Date());
            binding.setIsDeleted(0);

            agentMcpBindingDao.save(binding);

            // 同步写入 t_tool 表
            syncToolsToToolTable(binding, template);
            log.info("MCP binding created: code={}, agentCode={}, templateCode={}", bindingCode, agentCode, req.getTemplateId());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteBinding(String templateId, String headerAgentCode) {
        if (StringUtils.isBlank(templateId)) {
            throw BusinessException.paramError("模板 ID 不能为空");
        }

        String agentCode = headerAgentCode;
        if (StringUtils.isBlank(agentCode)) {
            throw BusinessException.paramError("Agent 编码不能为空");
        }

        AgentMcpBindingDO binding = agentMcpBindingDao.findByAgentAndTemplate(agentCode, templateId);
        if (binding == null) {
            throw BusinessException.notFound("绑定不存在");
        }

        // 解绑：改 enabled=0，保留 env_vars 等配置
        binding.setEnabled(0);
        binding.setUpdateTime(new Date());
        agentMcpBindingDao.updateById(binding);

        // 同步停用关联的 tool
        syncToolStatus(binding.getCode(), false);
        log.info("MCP binding unbound: agentCode={}, templateCode={}", agentCode, templateId);
    }

    // ---- 内部方法 ----

    private McpTemplateVO toTemplateVO(McpTemplateDO entity) {
        return McpTemplateVO.builder()
                .id(entity.getCode())
                .name(entity.getName())
                .description(entity.getDescription())
                .command(entity.getCommand())
                .args(parseArgs(entity.getArgs()))
                .runtimeAvailable(entity.getRuntimeAvailable() == 1)
                .runtimeEnv(runtimeEnvToString(entity.getRuntimeEnv()))
                .tools(parseTools(entity.getTools()))
                .createdAt(entity.getCreateTime() != null ? entity.getCreateTime().toString() : null)
                .updatedAt(entity.getUpdateTime() != null ? entity.getUpdateTime().toString() : null)
                .build();
    }

    private McpBindingVO toBindingVO(AgentMcpBindingDO entity) {
        // 获取模板名称等冗余信息
        McpTemplateDO template = mcpTemplateDao.getByCode(entity.getTemplateCode());
        String templateName = template != null ? template.getName() : "";
        String command = template != null ? template.getCommand() : "";
        String[] args = template != null ? parseArgs(template.getArgs()) : new String[0];
        boolean runtimeAvailable = template != null && template.getRuntimeAvailable() == 1;
        String runtimeEnv = template != null ? runtimeEnvToString(template.getRuntimeEnv()) : "none";

        return McpBindingVO.builder()
                .templateId(entity.getTemplateCode())
                .templateName(templateName)
                .command(command)
                .args(args)
                .code(entity.getCode())
                .agentCode(entity.getAgentCode())
                .enabled(entity.getEnabled() == 1)
                .envVars(entity.getEnvVars())
                .status(statusToString(entity.getStatus()))
                .runtimeAvailable(runtimeAvailable)
                .runtimeEnv(runtimeEnv)
                .tools(parseTools(entity.getToolsSnapshot()))
                .createdAt(entity.getCreateTime() != null ? entity.getCreateTime().toString() : null)
                .updatedAt(entity.getUpdateTime() != null ? entity.getUpdateTime().toString() : null)
                .build();
    }

    private void syncToolsToToolTable(AgentMcpBindingDO binding, McpTemplateDO template) {
        List<McpToolItem> tools = parseTools(binding.getToolsSnapshot());
        if (tools.isEmpty()) {
            return;
        }

        Date now = new Date();
        List<ToolDO> toolEntities = new ArrayList<>();

        for (McpToolItem tool : tools) {
            String toolCode = "mcp_" + binding.getCode() + "_" + tool.getName();
            // 检查是否已存在
            if (toolDao.existsByCode(toolCode)) {
                continue;
            }

            ToolDO toolDO = new ToolDO();
            toolDO.setCode(toolCode);
            toolDO.setName(tool.getName());
            toolDO.setDescription(StringUtils.defaultString(tool.getDescription()));
            toolDO.setParametersSchema(tool.getInputSchema() != null ? JsonUtils.toJson(tool.getInputSchema()) : "{}");
            toolDO.setSourceType(SourceType.MCP);
            toolDO.setSourceRef(binding.getCode() + ":" + tool.getName());
            toolDO.setOwnerAgentCode(binding.getAgentCode());
            toolDO.setStatus(binding.getEnabled() == 1 ? "ENABLED" : "DISABLED");
            toolDO.setRiskLevel("NORMAL");
            toolDO.setTimeoutMs(30000);
            toolDO.setCreateTime(now);
            toolDO.setUpdateTime(now);
            toolDO.setIsDeleted(0);
            toolEntities.add(toolDO);
        }

        if (!toolEntities.isEmpty()) {
            toolDao.saveBatch(toolEntities);
            log.info("Synced {} tools to t_tool for binding {}", toolEntities.size(), binding.getCode());
        }
    }

    private void syncToolStatus(String bindingCode, boolean enabled) {
        String status = enabled ? "ENABLED" : "DISABLED";
        toolDao.batchUpdateStatusBySourceRefPrefix(bindingCode, status);
    }

    private String[] parseArgs(String argsJson) {
        try {
            if (StringUtils.isBlank(argsJson) || "[]".equals(argsJson)) {
                return new String[0];
            }
            JsonNode node = MAPPER.readTree(argsJson);
            // 防御：如果数据库存的是双重序列化的字符串（如 "\"[...]\""），再解一层
            if (node.isTextual()) {
                String inner = node.asText();
                if (StringUtils.isNotBlank(inner) && !"[]".equals(inner)) {
                    node = MAPPER.readTree(inner);
                } else {
                    return new String[0];
                }
            }
            if (!node.isArray()) {
                return new String[0];
            }
            String[] result = new String[node.size()];
            for (int i = 0; i < node.size(); i++) {
                result[i] = node.get(i).asText();
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to parse args JSON: {}", argsJson, e);
            return new String[0];
        }
    }

    private List<McpToolItem> parseTools(String toolsJson) {
        try {
            if (StringUtils.isBlank(toolsJson) || "[]".equals(toolsJson)) {
                return List.of();
            }
            return parseToolsArray(toolsJson);
        } catch (Exception e) {
            log.warn("Failed to parse tools JSON: {}", toolsJson, e);
            return List.of();
        }
    }

    private int resolveRuntimeEnvInt(String runtimeEnv) {
        if (runtimeEnv == null) return 0;
        return switch (runtimeEnv.toLowerCase()) {
            case "electron" -> 1;
            case "backend" -> 2;
            default -> 0;
        };
    }

    private String runtimeEnvToString(Integer runtimeEnv) {
        if (runtimeEnv == null) return "none";
        return switch (runtimeEnv) {
            case 1 -> "electron";
            case 2 -> "backend";
            default -> "none";
        };
    }

    private String statusToString(Integer status) {
        if (status == null) return "stopped";
        return switch (status) {
            case 1 -> "running";
            case 2 -> "connecting";
            default -> "stopped";
        };
    }
}

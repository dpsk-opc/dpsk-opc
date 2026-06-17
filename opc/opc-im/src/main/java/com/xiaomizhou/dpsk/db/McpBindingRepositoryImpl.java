package com.xiaomizhou.dpsk.db;

import com.xiaomizhou.dpsk.db.dao.AgentMcpBindingDao;
import com.xiaomizhou.dpsk.db.dao.McpTemplateDao;
import com.xiaomizhou.dpsk.db.model.AgentMcpBindingDO;
import com.xiaomizhou.dpsk.db.model.McpTemplateDO;
import com.xiaomizhou.dpsk.tool.executor.McpToolExecutor;
import com.fasterxml.jackson.databind.JsonNode;
import com.xiaomizhou.dpsk.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 绑定信息仓库实现，为 McpToolExecutor 提供绑定信息查询。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/16
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class McpBindingRepositoryImpl implements McpToolExecutor.McpBindingRepository {

    private final AgentMcpBindingDao agentMcpBindingDao;
    private final McpTemplateDao mcpTemplateDao;

    @Override
    public McpToolExecutor.McpBindingInfo findByCode(String bindingCode) {
        AgentMcpBindingDO binding = agentMcpBindingDao.getByCode(bindingCode);
        if (binding == null) {
            return null;
        }

        McpToolExecutor.McpBindingInfo info = new McpToolExecutor.McpBindingInfo();
        info.setCode(binding.getCode());

        // 从模板表获取 command / args / runtimeEnv
        McpTemplateDO template = mcpTemplateDao.getByCode(binding.getTemplateCode());
        if (template != null) {
            info.setCommand(template.getCommand());
            info.setArgs(parseArgs(template.getArgs()));
            info.setRuntimeEnv(template.getRuntimeEnv());
        }

        // 解析 envVars
        String envVars = binding.getEnvVars();
        if (envVars != null && !envVars.isBlank()) {
            Map<String, String> envMap = new HashMap<>();
            for (String line : envVars.split("\n")) {
                line = line.trim();
                if (line.isEmpty()) continue;
                int eqIdx = line.indexOf('=');
                if (eqIdx > 0) {
                    envMap.put(line.substring(0, eqIdx).trim(), line.substring(eqIdx + 1).trim());
                }
            }
            info.setEnvVars(envMap);
        }

        return info;
    }

    private String[] parseArgs(String argsJson) {
        try {
            if (argsJson == null || argsJson.isBlank() || "[]".equals(argsJson)) {
                return new String[0];
            }
            JsonNode root = JsonUtils.OBJECT_MAPPER.readTree(argsJson);
            // 防御双重序列化：如果是文本节点，先解开
            if (root.isTextual()) {
                root = JsonUtils.OBJECT_MAPPER.readTree(root.asText());
            }
            if (!root.isArray()) {
                log.warn("Expected JSON array for args, got: {}", root.getNodeType());
                return new String[0];
            }
            List<String> list = new ArrayList<>();
            for (JsonNode node : root) {
                list.add(node.asText());
            }
            return list.toArray(new String[0]);
        } catch (Exception e) {
            log.warn("Failed to parse args JSON: {}", argsJson, e);
            return new String[0];
        }
    }
}

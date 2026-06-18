package com.xiaomizhou.dpsk.tool;

import com.google.common.collect.Lists;
import com.xiaomizhou.dpsk.tool.executor.ToolExecutorRouter;
import com.xiaomizhou.dpsk.tool.model.ToolCall;
import com.xiaomizhou.dpsk.tool.model.ToolContext;
import com.xiaomizhou.dpsk.tool.model.ToolMetadata;
import com.xiaomizhou.dpsk.tool.repository.ToolRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 工具注册中心。
 * <p>
 * 启动时从 DB 加载所有 ENABLED 工具到内存，支持热重载和工具搜索。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/30
 */
@Slf4j
public class ToolRegistry {

    /** 工具元数据缓存：key = tool name */
    private final Map<String, ToolMetadata> toolsByName = new ConcurrentHashMap<>();

    /** 工具元数据缓存：key = tool code */
    private final Map<String, ToolMetadata> toolsByCode = new ConcurrentHashMap<>();

    private final ToolRepository toolRepository;

    private final ToolExecutorRouter executorRouter;


    /** 是否已初始化 */
    private volatile boolean initialized = false;

    public ToolRegistry(ToolRepository toolRepository, ToolExecutorRouter executorRouter) {
        this.toolRepository = toolRepository;
        this.executorRouter = executorRouter;
    }

    /**
     * 从 DB 加载所有启用的工具到内存。
     */
    public void reload() {
        log.info("Reloading tool registry from database...");
        List<ToolMetadata> enabledTools = toolRepository.findAllEnabled();
        
        toolsByName.clear();
        toolsByCode.clear();
        
        for (ToolMetadata tool : enabledTools) {
            toolsByName.put(tool.getName(), tool);
            toolsByCode.put(tool.getCode(), tool);
        }

        initialized = true;
        log.info("Tool registry reloaded: {} tools loaded", enabledTools.size());
    }

    /**
     * 确保已初始化，若未初始化则自动加载。
     */
    public void ensureInitialized() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    reload();
                }
            }
        }
    }

    /**
     * 根据工具名称获取元数据。
     */
    public ToolMetadata getMetadata(String toolName/*, String agentCode*/) {
        ensureInitialized();
        return toolsByName.get(toolName);
    }

    /**
     * 根据工具编码获取元数据。
     */
    public ToolMetadata getByCode(String code) {
        ensureInitialized();
        return toolsByCode.get(code);
    }

    /**
     * 获取所有已注册的工具名称列表。
     */
    public List<String> getAllToolNames() {
        ensureInitialized();
        return new ArrayList<>(toolsByName.keySet());
    }

    /**
     * 获取所有已注册的工具元数据。
     */
    public List<ToolMetadata> getAllTools() {
        ensureInitialized();
        return new ArrayList<>(toolsByName.values());
    }

    /**
     * 根据 Agent 编码获取其可用工具（包含公共工具）。
     *
     * @param ownerAgentCode Agent 编码
     * @return 工具元数据列表
     */
    public List<ToolMetadata> getToolsForAgent(String ownerAgentCode) {
        ensureInitialized();
        return toolRepository.findByOwnerAgent(ownerAgentCode);
    }

    public List<ToolMetadata> getMetaTools(){
        ensureInitialized();
        return toolRepository.findByCategory("meta");
    }

    /**
     * 关键词搜索工具。
     *
     * @param keyword 关键词
     * @return 匹配的工具元数据列表
     */
    public List<ToolMetadata> searchTools(String keyword,String agentCode) {

        if (StringUtils.isBlank(agentCode)) {
            return List.of();
        }

        // local & mcp tools
        List<ToolMetadata> localTools = toolRepository.findByOwnerAgent(agentCode);

        String lowerKeyword = keyword.toLowerCase();
        return localTools.stream()
                .filter(t -> t.getName().toLowerCase().contains(lowerKeyword)
                        || t.getDescription().toLowerCase().contains(lowerKeyword)
                        || (t.getCategory() != null && t.getCategory().toLowerCase().contains(lowerKeyword))
                        || (t.getTags() != null && t.getTags().toLowerCase().contains(lowerKeyword)))
                .collect(Collectors.toList());
    }

    /**
     * 注册单个工具到内存（不持久化）。
     */
    public void register(ToolMetadata metadata) {
        toolsByName.put(metadata.getName(), metadata);
        toolsByCode.put(metadata.getCode(), metadata);
        log.debug("Tool registered: {} ({})", metadata.getName(), metadata.getCode());
    }

    /**
     * 从内存注销工具。
     */
    public void unregister(String toolName) {
        ToolMetadata removed = toolsByName.remove(toolName);
        if (removed != null) {
            toolsByCode.remove(removed.getCode());
            log.debug("Tool unregistered: {} ({})", toolName, removed.getCode());
        }
    }

    /**
     * 检查工具是否存在。
     */
    public boolean hasTool(String toolName) {
        ensureInitialized();
        return toolsByName.containsKey(toolName);
    }

    /**
     * 获取工具数量。
     */
    public int size() {
        return toolsByName.size();
    }

    /**
     * 获取执行器路由器。
     */
    public ToolExecutorRouter getExecutorRouter() {
        return executorRouter;
    }
}

package com.xiaomizhou.dpsk.tool.buildin;

import com.xiaomizhou.dpsk.tool.ToolMeta;
import com.xiaomizhou.dpsk.tool.ToolRegistry;
import com.xiaomizhou.dpsk.tool.model.ToolContext;
import com.xiaomizhou.dpsk.tool.model.ToolMetadata;
import dev.langchain4j.agent.tool.P;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 搜索工具的元工具。
 * <p>
 * LLM 可以通过此工具按需发现可用工具，实现工具发现机制。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/30
 */
@ToolMeta(value = "查询可用工具", level = "normal", category = "meta", tags = {"查询可用工具列表、工具详情"})
public class SearchTools {

    private final ToolRegistry registry;

    public SearchTools(ToolRegistry registry) {
        this.registry = registry;
    }

    @dev.langchain4j.agent.tool.Tool(name = "search_tools", value = "搜索可用工具，输入关键词返回匹配的工具列表")
    public String searchTools(
            @P(description = "搜索关键词，匹配工具名称、描述、分类和标签") String keyword,
            @P(description = "返回工具的最大数量，默认10") int limit, @P(value = "工具上下文，不能传这个参数",required = false) ToolContext context) {

        List<ToolMetadata> results = registry.searchTools(keyword, context.getAgentCode());

        if (results.isEmpty()) {
            return "未找到匹配关键词 \"" + keyword + "\" 的工具。";
        }

        int actualLimit = limit > 0 ? Math.min(limit, results.size()) : Math.min(10, results.size());

        return results.stream()
                .limit(actualLimit)
                .map(t -> String.format("- **%s** (%s): %s [category: %s, risk: %s]",
                        t.getName(), t.getCode(), t.getDescription(),
                        t.getCategory() != null ? t.getCategory() : "未分类",
                        t.getRiskLevel()))
                .collect(Collectors.joining("\n",
                        "找到 " + results.size() + " 个匹配工具，显示前 " + actualLimit + " 个：\n\n",
                        ""));
    }

    @dev.langchain4j.agent.tool.Tool(name = "get_tool_detail", value = "获取指定工具的详细参数Schema信息")
    public String getToolDetail(@P(description = "工具名称") String toolName,@P(value = "工具上下文，不能传这个参数",required = false) ToolContext context) {

        ToolMetadata metadata = registry.getMetadata(toolName);
        if (metadata == null) {
            return "未找到工具: " + toolName;
        }

        return String.format("""
                        工具详情:
                        - 名称: %s
                        - 编码: %s
                        - 描述: %s
                        - 分类: %s
                        - 来源类型: %s
                        - 风险等级: %s
                        - 超时: %dms
                        - 参数Schema: %s
                        """,
                metadata.getName(),
                metadata.getCode(),
                metadata.getDescription(),
                metadata.getCategory() != null ? metadata.getCategory() : "未分类",
                metadata.getSourceType(),
                metadata.getRiskLevel(),
                metadata.getTimeoutMs() != null ? metadata.getTimeoutMs() : 10000,
                metadata.getParametersSchema() != null ? metadata.getParametersSchema() : "{}");
    }
}

package com.xiaomizhou.dpsk.tool;

import com.xiaomizhou.dpsk.tool.model.ToolCall;
import com.xiaomizhou.dpsk.tool.model.ToolContext;
import com.xiaomizhou.dpsk.tool.model.ToolMetadata;
import com.xiaomizhou.dpsk.tool.model.ToolResult;

/**
 * 工具执行器接口，所有工具执行器需实现此接口。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/30
 */
public interface ToolExecutor {

    /**
     * 执行工具调用。
     * <p>
     * 返回结构化结果而非 String，便于上层判断成败并决定后续处理
     * （例如工具不可用时引导 LLM 先 add_tools）。
     *
     * @param call    工具调用请求
     * @param context 工具执行上下文
     * @param metadata 工具元数据
     * @return 结构化执行结果
     * @throws Exception 执行异常（由拦截器统一兜底转换为失败结果）
     */
    ToolResult execute(ToolCall call, ToolContext context, ToolMetadata metadata) throws Exception;
}

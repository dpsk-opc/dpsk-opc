package com.xiaomizhou.dpsk.tool;

import com.xiaomizhou.dpsk.tool.model.ToolCall;
import com.xiaomizhou.dpsk.tool.model.ToolContext;
import com.xiaomizhou.dpsk.tool.model.ToolMetadata;

/**
 * 工具执行器接口，所有工具执行器需实现此接口。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/30
 */
public interface ToolExecutor {

    /**
     * 执行工具调用。
     *
     * @param call    工具调用请求
     * @param context 工具执行上下文
     * @return 执行结果字符串
     * @throws Exception 执行异常
     */
    String execute(ToolCall call, ToolContext context, ToolMetadata metadata) throws Exception;
}

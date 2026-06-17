package com.xiaomizhou.dpsk.tool.executor;

import com.xiaomizhou.dpsk.tool.model.McpElectronRequest;
import com.xiaomizhou.dpsk.tool.model.McpElectronResult;

/**
 * MCP Electron 通信桥接。
 * core 模块只定义接口，具体实现在 im 模块通过 WebSocket 完成。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/16
 */
public interface McpElectronBridge {

    /**
     * 向前端 Electron 发送 MCP 工具调用请求，同步等待返回。
     *
     * @param request 调用请求
     * @return 执行结果
     * @throws Exception 执行失败或超时
     */
    McpElectronResult call(McpElectronRequest request) throws Exception;
}

package com.xiaomizhou.dpsk.tool.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP Electron 调用结果。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpElectronResult {

    /** 是否成功 */
    private boolean success;

    /** 成功时的返回内容 */
    private String result;

    /** 失败时的错误信息 */
    private String error;
}

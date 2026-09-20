package com.xiaomizhou.dpsk.tool.model;

import com.xiaomizhou.dpsk.tool.workspace.WorkspaceScope;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 工具执行上下文，包含会话、Agent、用户等信息，用于参数动态补全。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/30
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolContext {

    /** Agent 编码 */
    private String agentCode;

    /** 用户编码 */
    private String userCode;

    /** 会话编码 */
    private String conversationCode;

    /** 链路追踪ID */
    private String traceId;

    /**
     * 工作空间边界集合（含主工作空间与额外可写目录）。
     * <p>
     * 挂在 context 上传递，避免改动 {@code ToolExecutor} 及各执行器签名。
     */
    private WorkspaceScope workspaceScope;

    /**
     * 本轮用户消息文本。
     * <p>
     * 用于"用户明确给出路径即视为授权"的判定（产品方案 D9）：
     * 用户在消息里写出的路径，访问时不再弹确认框。
     */
    private String userContent;

    /** 扩展属性（环境变量等） */
    @Builder.Default
    private Map<String, Object> extra = new HashMap<>();
}

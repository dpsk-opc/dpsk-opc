package com.xiaomizhou.dpsk.tool.workspace;

import java.nio.file.Path;

/**
 * 授权查询接口（路径 → 是否已授权）。
 * <p>
 * 由 opc-im 提供会话级 / 绑定级预授权的实现；opc-core 仅依赖此接口。
 * <p>
 * <b>注意</b>：授权是按会话隔离的，必须显式传入会话编码，禁止做"任一会话授权即放行"的判定
 * （那会导致跨会话授权泄露）。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/9/20
 */
public interface WorkspaceAuthorization {

    /**
     * 判断（已规范化的）路径在指定会话下是否已获得授权。
     *
     * @param conversationCode 会话编码
     * @param normalizedPath   规范化后的绝对路径
     * @param scope            当前工作空间边界（用于限定授权范围）
     * @return 已授权返回 true
     */
    boolean isAuthorized(String conversationCode, Path normalizedPath, WorkspaceScope scope);
}

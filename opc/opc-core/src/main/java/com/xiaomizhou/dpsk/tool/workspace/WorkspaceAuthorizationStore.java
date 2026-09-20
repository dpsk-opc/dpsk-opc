package com.xiaomizhou.dpsk.tool.workspace;

import java.nio.file.Path;

/**
 * 会话级授权存储。
 * <p>
 * 用户确认"本次会话允许该目录"后写入；会话结束失效。避免历史授权变成常驻后门。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/9/20
 */
public interface WorkspaceAuthorizationStore extends WorkspaceAuthorization {

    /**
     * 记录一个会话级授权目录。
     *
     * @param conversationCode 会话编码
     * @param dir              被授权的目录（绝对路径）
     */
    void grantDirectory(String conversationCode, Path dir);

    /**
     * 清理指定会话的全部授权。
     */
    void clear(String conversationCode);

    @Override
    default boolean isAuthorized(String conversationCode, Path normalizedPath, WorkspaceScope scope) {
        return false;
    }
}

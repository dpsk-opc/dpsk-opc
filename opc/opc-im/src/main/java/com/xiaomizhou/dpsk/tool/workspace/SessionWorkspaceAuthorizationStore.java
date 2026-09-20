package com.xiaomizhou.dpsk.tool.workspace;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentMap;

/**
 * 会话级工作空间授权存储（内存实现）。
 * <p>
 * 用户在确认卡片选择"本次会话允许该目录"后写入；会话结束失效，
 * 避免一次授权永久开口子（产品方案 3.2 的硬约束）。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/9/20
 */
@Slf4j
@Component
public class SessionWorkspaceAuthorizationStore implements WorkspaceAuthorizationStore {

    /**
     * conversationCode -> 已授权目录列表
     */
    private final ConcurrentMap<String, List<Path>> authorizedDirs = new ConcurrentHashMap<>();

    @Override
    public boolean isAuthorized(String conversationCode, Path normalizedPath, WorkspaceScope scope) {
        if (StringUtils.isBlank(conversationCode) || normalizedPath == null) {
            return false;
        }
        List<Path> dirs = authorizedDirs.get(conversationCode);
        if (CollectionUtils.isEmpty(dirs)) {
            return false;
        }
        for (Path dir : dirs) {
            try {
                Path realDir = Files.exists(dir) ? dir.toRealPath() : dir;
                if (normalizedPath.startsWith(realDir.normalize())) {
                    return true;
                }
            } catch (IOException e) {
                if (normalizedPath.startsWith(dir.normalize())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void grantDirectory(String conversationCode, Path dir) {
        if (StringUtils.isBlank(conversationCode) || dir == null) {
            return;
        }
        authorizedDirs.computeIfAbsent(conversationCode, k -> new CopyOnWriteArrayList<>())
                .add(dir.normalize());
        log.info("Granted session workspace authorization: conversation={}, dir={}", conversationCode, dir);
    }

    @Override
    public void clear(String conversationCode) {
        if (StringUtils.isBlank(conversationCode)) {
            return;
        }
        authorizedDirs.remove(conversationCode);
        log.info("Cleared session workspace authorization: conversation={}", conversationCode);
    }
}

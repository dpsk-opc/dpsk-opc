package com.xiaomizhou.dpsk.tool.workspace;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 路径校验内核（纯逻辑，无状态，便于单测）。
 * <p>
 * 处理顺序<b>不可调换</b>：
 * <pre>
 *   路径规范化 → 高危判定 → 边界判定 → 授权判定
 * </pre>
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/9/20
 */
@Slf4j
public class PathGuard {

    /**
     * 校验结论。
     */
    public enum Decision {
        /** 在工作空间 / 公共产出目录内，放行 */
        ALLOW,
        /** 命中会话授权 / 绑定预授权，放行并留痕 */
        ALLOW_AUTHORIZED,
        /** 越界且无授权，需要用户确认 */
        NEED_CONFIRM,
        /** 命中高危路径，直接拒绝 */
        DENY_PROTECTED
    }

    private final ProtectedPathPolicy protectedPathPolicy;

    public PathGuard(ProtectedPathPolicy protectedPathPolicy) {
        this.protectedPathPolicy = protectedPathPolicy == null
                ? ProtectedPathPolicy.defaultPolicy()
                : protectedPathPolicy;
    }

    public static PathGuard createDefault() {
        return new PathGuard(ProtectedPathPolicy.defaultPolicy());
    }

    /**
     * 校验一个路径访问。
     *
     * @param access           访问事实
     * @param scope            工作空间边界集合
     * @param authorization    会话授权查询（可为 null）
     * @param conversationCode 会话编码（授权按会话隔离，必须显式传入）
     * @return 校验结论
     */
    public PathVerdict check(PathAccess access, WorkspaceScope scope,
                             WorkspaceAuthorization authorization, String conversationCode) {
        String rawPath = access == null ? null : access.getRawPath();

        // 0. 空路径：不构成文件访问，放行（交给工具自身做参数校验）
        if (StringUtils.isBlank(rawPath)) {
            return PathVerdict.builder()
                    .decision(Decision.ALLOW)
                    .rawPath(rawPath)
                    .direction(access == null ? PathDirection.READ : access.getDirection())
                    .reason("路径为空，跳过边界校验")
                    .build();
        }

        // 1. 路径规范化（相对基准 = 主工作空间）
        Path normalized = normalize(rawPath, scope);
        if (normalized == null) {
            // 无法解析的路径：保守处理，要求确认（防"看起来不像路径"的绕过）
            return PathVerdict.builder()
                    .decision(Decision.NEED_CONFIRM)
                    .rawPath(rawPath)
                    .direction(access.getDirection())
                    .reason("路径无法解析")
                    .build();
        }

        // 2. 工作空间未配置 → 不做边界限制（保持存量行为），但仍拦高危路径
        if (scope == null || scope.isEmpty()) {
            return highRiskVerdict(access, normalized, "工作空间未配置，仅做高危路径校验", true);
        }

        // 3. 高危判定（优先于边界判定：即便"授权"也不放行系统目录）
        PathVerdict protectedVerdict = checkProtected(access, normalized);
        if (protectedVerdict != null) {
            return protectedVerdict;
        }

        // 4. 边界判定：主工作空间 + 额外可写目录
        for (Path allowed : scope.allowedDirs()) {
            Path allowedNormalized = normalizeAllowed(allowed, scope);
            if (allowedNormalized != null && contains(allowedNormalized, normalized)) {
                return PathVerdict.builder()
                        .decision(Decision.ALLOW)
                        .rawPath(rawPath)
                        .normalizedPath(normalized)
                        .direction(access.getDirection())
                        .reason("在工作空间内: " + allowedNormalized)
                        .build();
            }
        }

        // 5. 授权判定（按会话隔离）
        if (authorization != null && authorization.isAuthorized(conversationCode, normalized, scope)) {
            return PathVerdict.builder()
                    .decision(Decision.ALLOW_AUTHORIZED)
                    .rawPath(rawPath)
                    .normalizedPath(normalized)
                    .direction(access.getDirection())
                    .reason("命中会话授权")
                    .build();
        }

        // 6. 越界且无授权 → 需要确认
        return PathVerdict.builder()
                .decision(Decision.NEED_CONFIRM)
                .rawPath(rawPath)
                .normalizedPath(normalized)
                .direction(access.getDirection())
                .reason("路径不在工作空间内且未获得用户授权")
                .build();
    }

    /**
     * 工作空间未配置时的降级校验：只拦高危路径，其余放行。
     */
    private PathVerdict highRiskVerdict(PathAccess access, Path normalized, String reason, boolean allowOtherwise) {
        PathVerdict verdict = checkProtected(access, normalized);
        if (verdict != null) {
            return verdict;
        }
        return PathVerdict.builder()
                .decision(Decision.ALLOW)
                .rawPath(access.getRawPath())
                .normalizedPath(normalized)
                .direction(access.getDirection())
                .reason(reason)
                .build();
    }

    /**
     * 高危路径判定。
     */
    private PathVerdict checkProtected(PathAccess access, Path normalized) {
        if (protectedPathPolicy.isProtected(normalized)) {
            log.warn("Protected path access denied: path={}, direction={}", normalized, access.getDirection());
            return PathVerdict.builder()
                    .decision(Decision.DENY_PROTECTED)
                    .rawPath(access.getRawPath())
                    .normalizedPath(normalized)
                    .direction(access.getDirection())
                    .reason("该位置属于系统受保护区域: " + normalized)
                    .build();
        }
        return null;
    }

    /**
     * 路径规范化。
     * <p>
     * 处理要点：
     * <ul>
     *   <li>相对路径基于 {@code primaryWorkspace} 解析，而非进程 CWD；</li>
     *   <li>符号链接 / 目录联接解析为真实路径；</li>
     *   <li>解析 {@code ..} 目录穿越；</li>
     *   <li>GLOB 模式取最长公共前缀目录。</li>
     * </ul>
     *
     * @return 规范化后的绝对路径；无法解析返回 null
     */
    public Path normalize(String rawPath, WorkspaceScope scope) {
        if (StringUtils.isBlank(rawPath)) {
            return null;
        }
        try {
            String candidate = rawPath.trim();

            // 去掉引号包裹
            if ((candidate.startsWith("\"") && candidate.endsWith("\""))
                    || (candidate.startsWith("'") && candidate.endsWith("'"))) {
                candidate = candidate.substring(1, candidate.length() - 1);
            }

            // 展开 Windows 长路径前缀 \\?\
            if (candidate.startsWith("\\\\?\\")) {
                candidate = candidate.substring(4);
            }

            Path path = Paths.get(candidate);

            // 相对路径 → 基于主工作空间解析
            if (!path.isAbsolute()) {
                Path base = scope == null ? null : scope.primaryPath();
                if (base == null) {
                    // 没有基准：用进程 CWD 只能作为最后手段（仅用于高危判定，不用于边界判定）
                    base = Paths.get(System.getProperty("user.dir", "."));
                }
                path = base.resolve(path);
            }

            // 取最长公共前缀目录（GLOB）—— 由调用方在 PathAccess.kind 中表达，这里统一处理通配符
            path = stripGlob(path);

            // 解析符号链接 / 目录联接（路径不存在时 toRealPath 会失败，降级为 normalize）
            Path normalized = toRealPathSafely(path);

            // 去除冗余 . / ..
            return normalized.normalize();

        } catch (InvalidPathException e) {
            log.warn("Invalid path: {}", rawPath, e);
            return null;
        } catch (Exception e) {
            log.warn("Failed to normalize path: {}", rawPath, e);
            return null;
        }
    }

    /**
     * 对允许目录也做真实路径解析，避免"符号链接绕过"（D:\A\link → C:\Windows）。
     */
    private Path normalizeAllowed(Path allowed, WorkspaceScope scope) {
        try {
            return toRealPathSafely(allowed).normalize();
        } catch (Exception e) {
            return allowed.normalize();
        }
    }

    /**
     * 解析真实路径；路径不存在时逐级向上找到已存在的父目录再拼接，保证符号链接被解析。
     */
    private Path toRealPathSafely(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            // 路径不存在（写新文件场景）：向上找最近的已存在父目录，解析后再拼回剩余部分
            Path existing = path;
            Path remaining = null;
            while (existing != null && !Files.exists(existing)) {
                Path fileName = existing.getFileName();
                remaining = remaining == null ? fileName : fileName.resolve(remaining);
                existing = existing.getParent();
            }
            if (existing == null) {
                return path;
            }
            try {
                Path realExisting = existing.toRealPath();
                return remaining == null ? realExisting : realExisting.resolve(remaining);
            } catch (IOException ex) {
                return path;
            }
        }
    }

    /**
     * 通配模式取最长公共前缀目录。
     * <p>
     * 避免 {@code **}{@code /*} 被当作"授权全盘"。
     */
    private Path stripGlob(Path path) {
        Path root = path.getRoot();
        Path result = root;
        for (Path segment : path) {
            String name = segment.toString();
            if (name.contains("*") || name.contains("?") || name.contains("[")) {
                break;
            }
            result = result == null ? segment : result.resolve(segment);
        }
        return result == null ? path : result;
    }

    /**
     * 路径段包含判定。
     * <p>
     * 必须使用 {@link Path#startsWith} 语义，确保 {@code D:\A-evil} 不被判为在 {@code D:\A} 内，
     * 而不是简单字符串 {@code startsWith}。
     */
    public boolean contains(Path parent, Path child) {
        if (parent == null || child == null) {
            return false;
        }
        return child.startsWith(parent.normalize());
    }
}

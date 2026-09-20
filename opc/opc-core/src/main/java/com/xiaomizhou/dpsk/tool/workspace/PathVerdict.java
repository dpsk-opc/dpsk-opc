package com.xiaomizhou.dpsk.tool.workspace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.file.Path;

/**
 * 单个路径的校验结论。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/9/20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PathVerdict {

    /** 结论类型 */
    private PathGuard.Decision decision;

    /** 原始路径 */
    private String rawPath;

    /** 规范化后的绝对路径 */
    private Path normalizedPath;

    /** 访问方向 */
    private PathDirection direction;

    /** 命中说明（拒绝原因 / 命中哪个目录） */
    private String reason;

    /**
     * 是否放行（含已授权）。
     */
    public boolean isAllowed() {
        return decision == PathGuard.Decision.ALLOW
                || decision == PathGuard.Decision.ALLOW_AUTHORIZED;
    }

    /**
     * 是否需要用户确认。
     */
    public boolean isNeedConfirm() {
        return decision == PathGuard.Decision.NEED_CONFIRM;
    }

    /**
     * 是否被硬性拒绝（高危路径）。
     */
    public boolean isDenied() {
        return decision == PathGuard.Decision.DENY_PROTECTED;
    }
}

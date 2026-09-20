package com.xiaomizhou.dpsk.tool.workspace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 执行期的工作空间边界集合。
 * <p>
 * 一次工具执行可能同时受多个目录约束：
 * <ul>
 *   <li>{@code primaryWorkspace}：当前 Agent 的工作空间，也是相对路径的解析基准；</li>
 *   <li>{@code extraWritableDirs}：额外可写目录（群 / 专家团的公共产出目录）。</li>
 * </ul>
 * <p>
 * 群 / 专家团的工作空间只作为"额外可写目录"，<b>不放宽成员自身边界</b>，
 * 避免"进群即提权"与跨 Agent 私有数据泄露。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/9/20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceScope {

    /** 产出目录名 */
    public static final String DIR_OUTPUT = "output";

    /** 临时目录名 */
    public static final String DIR_TMP = "tmp";

    /** 脚本目录名 */
    public static final String DIR_SCRIPTS = "scripts";

    /** 主工作空间（Agent 的工作空间），相对路径基准 */
    private String primaryWorkspace;

    /** 额外可写目录（群 / 专家团公共产出目录） */
    @Builder.Default
    private List<String> extraWritableDirs = new ArrayList<>();

    /**
     * 是否已配置工作空间。未配置时不做边界校验（保持存量行为）。
     */
    public boolean isEmpty() {
        return primaryWorkspace == null || primaryWorkspace.isBlank();
    }

    /**
     * 主工作空间路径（未配置返回 null）。
     */
    public Path primaryPath() {
        return isEmpty() ? null : Paths.get(primaryWorkspace);
    }

    /**
     * 产出目录（未配置返回 null）。
     */
    public Path outputPath() {
        Path primary = primaryPath();
        return primary == null ? null : primary.resolve(DIR_OUTPUT);
    }

    /**
     * 允许的目录列表（主工作空间 + 额外可写目录）。
     */
    public List<Path> allowedDirs() {
        List<Path> dirs = new ArrayList<>();
        Path primary = primaryPath();
        if (primary != null) {
            dirs.add(primary);
        }
        if (extraWritableDirs != null) {
            for (String dir : extraWritableDirs) {
                if (dir != null && !dir.isBlank()) {
                    dirs.add(Paths.get(dir));
                }
            }
        }
        return dirs;
    }

    /**
     * 额外可写目录（排除主工作空间）路径列表。
     */
    public List<Path> extraPaths() {
        List<Path> dirs = new ArrayList<>();
        if (extraWritableDirs != null) {
            for (String dir : extraWritableDirs) {
                if (dir != null && !dir.isBlank()) {
                    dirs.add(Paths.get(dir));
                }
            }
        }
        return dirs;
    }
}

package com.xiaomizhou.dpsk.tool.workspace;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * 工作空间边界解析器。
 * <p>
 * 把 AgentBuildSpec 上的三类 workspace 组装成执行期的 {@link WorkspaceScope}：
 * <ul>
 *   <li>单聊：Agent 自己的 workspace 为主边界；</li>
 *   <li>群聊：Agent 的 workspace 为主边界 + 群 workspace 作为额外可写目录；</li>
 *   <li>专家团：本期仅 Agent 自身边界（团队公共产出目录下期实现）。</li>
 * </ul>
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/9/20
 */
@Slf4j
public class WorkspaceResolver {

    private final WorkspaceProperties properties;

    public WorkspaceResolver(WorkspaceProperties properties) {
        this.properties = properties == null ? new WorkspaceProperties() : properties;
    }

    /**
     * 解析执行期边界集合。
     *
     * @param agentWorkspace Agent 配置的 workspace（可为空）
     * @param agentCode      Agent 编码（用于默认值推导）
     * @param sharedWorkspace 群 / 专家团的公共产出目录（可为空）
     * @return 边界集合；未启用或未配置时返回空 scope
     */
    public WorkspaceScope resolve(String agentWorkspace, String agentCode, String sharedWorkspace) {
        if (!properties.isEnabled()) {
            return WorkspaceScope.builder().build();
        }
        String primary = properties.resolve(agentWorkspace, agentCode);
        WorkspaceScope.WorkspaceScopeBuilder builder = WorkspaceScope.builder()
                .primaryWorkspace(primary);

        // 公共产出目录只作为"额外可写目录"，不放宽成员边界（防跨 Agent 私有数据泄露）
        if (StringUtils.isNotBlank(sharedWorkspace)
                && !StringUtils.equalsIgnoreCase(sharedWorkspace, primary)) {
            builder.extraWritableDirs(java.util.List.of(sharedWorkspace.trim()));
        }

        WorkspaceScope scope = builder.build();
        if (scope.isEmpty()) {
            log.debug("Workspace not configured for agent='{}', boundary check degraded to protected-path only", agentCode);
        }
        return scope;
    }
}

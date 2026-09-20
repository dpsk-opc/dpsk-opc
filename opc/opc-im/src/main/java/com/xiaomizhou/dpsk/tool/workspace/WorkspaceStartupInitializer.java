package com.xiaomizhou.dpsk.tool.workspace;

import com.xiaomizhou.dpsk.db.ChatGroupComponent;
import com.xiaomizhou.dpsk.db.dao.AgentDao;
import com.xiaomizhou.dpsk.db.model.Agent;
import com.xiaomizhou.dpsk.db.model.ChatGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * 启动期工作空间初始化。
 * <p>
 * 职责：
 * <ol>
 *   <li><b>默认值回填</b>：数据库 workspace 为空的 Agent / 群，按
 *       {@code <root>/<实体编码>} 计算默认值并写回数据库，
 *       使配置页与运行期口径一致、便于前端展示与排查；</li>
 *   <li><b>创建目录</b>：为每个工作空间创建目录与标准子目录（幂等）。</li>
 * </ol>
 * <p>
 * <b>执行时机</b>：{@link ApplicationReadyEvent}（晚于 Flyway 数据库迁移）。
 * 通过 {@link Order} 保证在工具注册（order=0）之后、内置任务（order=20）之前执行。
 * <p>
 * 失败只告警，不阻塞启动。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/9/20
 */
@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class WorkspaceStartupInitializer {

    private final WorkspaceProperties properties;

    private final WorkspaceInitializer initializer;

    private final AgentDao agentDao;

    private final ChatGroupComponent chatGroupComponent;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        if (!properties.isEnabled()) {
            log.info("Workspace initialization skipped: workspace feature disabled");
            return;
        }
        if (StringUtils.isBlank(properties.getRoot())) {
            log.warn("Workspace root not configured "
                    + "(com.xiaomizhou.dpsk.opc.workspace.root), skip workspace initialization");
            return;
        }

        int agentCount = initAgentWorkspaces();
        int groupCount = initGroupWorkspaces();

        log.info("Workspace initialization complete: {} agent workspaces, {} group workspaces (root={})",
                agentCount, groupCount, properties.getRoot());
    }

    /**
     * 初始化 Agent 工作空间，并回填默认值。
     */
    private int initAgentWorkspaces() {
        int count = 0;
        try {
            List<Agent> agents = agentDao.list();
            if (CollectionUtils.isEmpty(agents)) {
                return 0;
            }
            for (Agent agent : agents) {
                String resolved = properties.resolve(agent.getWorkspace(), agent.getCode());
                if (StringUtils.isBlank(resolved)) {
                    continue;
                }
                // 数据库为空时回填默认值，保证配置页与运行期一致
                if (StringUtils.isBlank(agent.getWorkspace())) {
                    backfillAgentWorkspace(agent, resolved);
                }
                if (initializer.initialize(WorkspaceScope.builder().primaryWorkspace(resolved).build())) {
                    count++;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to initialize agent workspaces", e);
        }
        return count;
    }

    /**
     * 初始化群工作空间，并回填默认值。
     */
    private int initGroupWorkspaces() {
        int count = 0;
        try {
            List<ChatGroup> groups = chatGroupComponent.listAll();
            if (CollectionUtils.isEmpty(groups)) {
                return 0;
            }
            for (ChatGroup group : groups) {
                String resolved = properties.resolve(group.getWorkspace(), group.getCode());
                if (StringUtils.isBlank(resolved)) {
                    continue;
                }
                if (StringUtils.isBlank(group.getWorkspace())) {
                    backfillGroupWorkspace(group, resolved);
                }
                if (initializer.initialize(WorkspaceScope.builder().primaryWorkspace(resolved).build())) {
                    count++;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to initialize group workspaces", e);
        }
        return count;
    }

    /**
     * 回填 Agent 的默认工作空间。
     * <p>
     * 注意：update_time 列 NOT NULL，且项目未配置 MyBatis-Plus 的 MetaObjectHandler
     * 自动填充，因此必须手动 setUpdateTime，否则会触发 NULL 约束异常。
     */
    private void backfillAgentWorkspace(Agent agent, String workspace) {
        try {
            Agent update = new Agent();
            update.setId(agent.getId());
            update.setWorkspace(workspace);
            update.setUpdateTime(new Date());
            agentDao.updateById(update);
            log.info("Backfilled default workspace for agent '{}': {}", agent.getCode(), workspace);
        } catch (Exception e) {
            log.warn("Failed to backfill workspace for agent '{}'", agent.getCode(), e);
        }
    }

    /**
     * 回填群的默认工作空间。
     */
    private void backfillGroupWorkspace(ChatGroup group, String workspace) {
        try {
            chatGroupComponent.updateGroup(group.getCode(), null, null, workspace);
            log.info("Backfilled default workspace for group '{}': {}", group.getCode(), workspace);
        } catch (Exception e) {
            log.warn("Failed to backfill workspace for group '{}'", group.getCode(), e);
        }
    }
}

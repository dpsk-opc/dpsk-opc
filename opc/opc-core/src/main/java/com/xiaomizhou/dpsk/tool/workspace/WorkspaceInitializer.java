package com.xiaomizhou.dpsk.tool.workspace;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 工作空间初始化器：确保目录存在，并创建标准子目录结构。
 * <p>
 * <pre>
 *   &lt;workspace&gt;/
 *   ├── output/     产出目录（唯一面向用户的产出展示目录）
 *   ├── tmp/        临时目录（不对外展示）
 *   └── scripts/    脚本目录
 * </pre>
 * 幂等，重复调用无副作用。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/9/20
 */
@Slf4j
public class WorkspaceInitializer {

    private final WorkspaceProperties properties;

    public WorkspaceInitializer(WorkspaceProperties properties) {
        this.properties = properties == null ? new WorkspaceProperties() : properties;
    }

    /**
     * 初始化工作空间。
     *
     * @param scope 边界集合
     * @return true 表示初始化成功（或无需初始化）
     */
    public boolean initialize(WorkspaceScope scope) {
        if (scope == null || scope.isEmpty()) {
            return true;
        }
        try {
            Files.createDirectories(scope.primaryPath());
            if (properties.isAutoCreateStructure()) {
                createStructure(scope.primaryPath());
            }
            return true;
        } catch (IOException e) {
            log.warn("Failed to initialize workspace: {}", scope.getPrimaryWorkspace(), e);
            return false;
        }
    }

    /**
     * 创建标准子目录结构。
     */
    public void createStructure(Path workspace) throws IOException {
        for (String dir : new String[]{WorkspaceScope.DIR_OUTPUT, WorkspaceScope.DIR_TMP, WorkspaceScope.DIR_SCRIPTS}) {
            Files.createDirectories(workspace.resolve(dir));
        }
        log.debug("Workspace structure created: {}", workspace);
    }

    /**
     * 按实体编码初始化默认工作空间（启动期用）。
     */
    public void initializeDefault(String ownerCode) {
        String workspace = properties.resolveDefault(ownerCode);
        if (StringUtils.isBlank(workspace)) {
            return;
        }
        initialize(WorkspaceScope.builder().primaryWorkspace(workspace).build());
    }
}

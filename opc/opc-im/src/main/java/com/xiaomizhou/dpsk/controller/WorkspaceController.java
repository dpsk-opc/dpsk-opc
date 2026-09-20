package com.xiaomizhou.dpsk.controller;

import com.xiaomizhou.dpsk.core.model.Results;
import com.xiaomizhou.dpsk.core.model.response.Response;
import com.xiaomizhou.dpsk.db.AgentComponent;
import com.xiaomizhou.dpsk.db.ChatGroupComponent;
import com.xiaomizhou.dpsk.db.dto.AgentDto;
import com.xiaomizhou.dpsk.tool.workspace.WorkspaceProperties;
import com.xiaomizhou.dpsk.tool.workspace.WorkspaceScope;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 工作空间产出物控制器。
 * <p>
 * 本期简化：<b>不做文件归属映射</b>，直接扫描工作空间 {@code output/} 目录返回内容。
 * 不区分"哪个会话/哪个 Agent 产出"，因此不提供本会话/历史分类
 * （出参预留 conversationCode / agentCode 可空字段，便于后续扩展）。
 * <p>
 * 基准路径: GET /xiaomizhou/opc/v1/workspace
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/9/20
 */
@RestController
@RequestMapping(value = "xiaomizhou/opc/v1/workspace")
@Slf4j
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceProperties workspaceProperties;

    private final AgentComponent agentComponent;

    private final ChatGroupComponent chatGroupComponent;

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /**
     * 查询工作空间产出物列表。
     *
     * @param ownerType AGENT（默认）/ GROUP
     * @param ownerCode Agent 或群的编码
     * @param scope     PUBLIC（工作空间 output/，默认）/ EXTERNAL（外部路径，本期未实现返回空）
     * @param keyword   可选，文件名过滤（不区分大小写）
     */
    @GetMapping(value = "files")
    public Response<WorkspaceFilesVo> files(@RequestParam(value = "ownerType", required = false, defaultValue = "AGENT") String ownerType,
                                            @RequestParam(value = "ownerCode") String ownerCode,
                                            @RequestParam(value = "scope", required = false, defaultValue = "PUBLIC") String scope,
                                            @RequestParam(value = "keyword", required = false) String keyword) {
        if (StringUtils.isBlank(ownerCode)) {
            return Results.fail("ownerCode不能为空");
        }

        String workspace = resolveWorkspace(ownerType, ownerCode);
        if (StringUtils.isBlank(workspace)) {
            WorkspaceFilesVo empty = new WorkspaceFilesVo(ownerType, ownerCode, null, new ArrayList<>());
            return Results.ok(empty);
        }

        Path outputDir = Paths.get(workspace, WorkspaceScope.DIR_OUTPUT);
        List<WorkspaceFileItem> items = new ArrayList<>();
        if (Files.isDirectory(outputDir)) {
            try (Stream<Path> stream = Files.walk(outputDir)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> StringUtils.isBlank(keyword)
                                || p.getFileName().toString().toLowerCase().contains(keyword.toLowerCase()))
                        .forEach(p -> items.add(toItem(p, workspace)));
            } catch (IOException e) {
                log.warn("Failed to list workspace output dir: {}", outputDir, e);
            }
        }

        items.sort(Comparator.comparing(WorkspaceFileItem::getLastModifiedTime,
                Comparator.nullsLast(Comparator.reverseOrder())));

        return Results.ok(new WorkspaceFilesVo(ownerType, ownerCode, workspace, items));
    }

    /**
     * 解析工作空间：用户配置优先，否则用默认值（与运行期口径一致）。
     */
    private String resolveWorkspace(String ownerType, String ownerCode) {
        if ("GROUP".equalsIgnoreCase(ownerType)) {
            String configured = chatGroupComponent.getWorkspace(ownerCode);
            return workspaceProperties.resolve(configured, ownerCode);
        }
        AgentDto agent = agentComponent.getByCode(ownerCode);
        String configured = agent == null ? null : agent.getWorkspace();
        return workspaceProperties.resolve(configured, ownerCode);
    }

    private WorkspaceFileItem toItem(Path file, String workspace) {
        WorkspaceFileItem item = new WorkspaceFileItem();
        try {
            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
            item.setSize(attrs.size());
            item.setLastModifiedTime(TIME_FORMATTER.format(
                    Instant.ofEpochMilli(attrs.lastModifiedTime().toMillis())));
        } catch (IOException e) {
            log.warn("Failed to read file attributes: {}", file, e);
        }
        item.setFileName(file.getFileName().toString());
        // 工作空间内以相对路径展示
        try {
            item.setRelativePath(Paths.get(workspace).relativize(file).toString().replace('\\', '/'));
        } catch (Exception e) {
            item.setRelativePath(file.getFileName().toString());
        }
        item.setAbsolutePath(file.toAbsolutePath().toString());
        item.setFileType(resolveFileType(file.getFileName().toString()));
        item.setOutsideWorkspace(false);
        return item;
    }

    private String resolveFileType(String fileName) {
        int idx = fileName.lastIndexOf('.');
        return idx < 0 ? "unknown" : fileName.substring(idx + 1).toLowerCase();
    }

    /**
     * 产出物列表结果。
     */
    @Data
    public static class WorkspaceFilesVo {
        private String ownerType;
        private String ownerCode;
        private String workspace;
        private List<WorkspaceFileItem> files;

        public WorkspaceFilesVo(String ownerType, String ownerCode, String workspace, List<WorkspaceFileItem> files) {
            this.ownerType = ownerType;
            this.ownerCode = ownerCode;
            this.workspace = workspace;
            this.files = files;
        }
    }

    /**
     * 单个产出物。
     */
    @Data
    public static class WorkspaceFileItem {
        private String fileName;
        /** 工作空间内相对路径 */
        private String relativePath;
        /** 绝对路径 */
        private String absolutePath;
        private long size;
        private String lastModifiedTime;
        private String fileType;
        /** 是否在工作空间之外（外部路径需显著标注） */
        private boolean outsideWorkspace;
        /** 预留：产出会话编码（本期不填充） */
        private String conversationCode;
        /** 预留：产出 Agent 编码（本期不填充） */
        private String agentCode;
    }
}

package com.xiaomizhou.dpsk.tool.workspace;

import com.xiaomizhou.dpsk.tool.SourceType;
import com.xiaomizhou.dpsk.tool.model.ToolContext;
import com.xiaomizhou.dpsk.tool.model.ToolExecutionResult;
import com.xiaomizhou.dpsk.tool.model.ToolMetadata;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 路径访问校验服务：把「能力位判定 → 路径获取 → PathGuard → 确认交互」串起来。
 * <p>
 * 入口是 {@link #validate(ToolMetadata, ToolContext, Map)}，返回 {@code null} 表示放行。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/9/20
 */
@Slf4j
public class PathAccessValidator {

    private final PathGuard pathGuard;
    private final PathExtractor pathExtractor;
    private final PathAccessConfirmer confirmer;
    private final WorkspaceAuthorizationStore authorizationStore;
    private final WorkspaceProperties properties;

    /**
     * 用户消息路径授权匹配器（D9）。可为 null（关闭该能力）。
     */
    private final UserPathAuthorizationMatcher userPathMatcher;

    public PathAccessValidator(PathGuard pathGuard,
                               PathExtractor pathExtractor,
                               PathAccessConfirmer confirmer,
                               WorkspaceAuthorizationStore authorizationStore,
                               WorkspaceProperties properties) {
        this(pathGuard, pathExtractor, confirmer, authorizationStore, properties, null);
    }

    public PathAccessValidator(PathGuard pathGuard,
                               PathExtractor pathExtractor,
                               PathAccessConfirmer confirmer,
                               WorkspaceAuthorizationStore authorizationStore,
                               WorkspaceProperties properties,
                               UserPathAuthorizationMatcher userPathMatcher) {
        this.pathGuard = pathGuard;
        this.pathExtractor = pathExtractor;
        this.confirmer = confirmer;
        this.authorizationStore = authorizationStore;
        this.properties = properties == null ? new WorkspaceProperties() : properties;
        this.userPathMatcher = userPathMatcher;
    }

    /**
     * 校验一次工具调用的路径访问。
     *
     * @param metadata 工具元数据（含能力位与路径参数声明）
     * @param context  执行上下文（含 workspaceScope）
     * @param params   调用参数
     * @return {@code null} 表示放行；非 null 表示拒绝的执行结果
     */
    public ToolExecutionResult validate(ToolMetadata metadata, ToolContext context, Map<String, Object> params) {
        if (metadata == null || !properties.isEnabled()) {
            return null;
        }

        // 1. 能力位判定（D13 + D19）
        //
        // 进入校验的条件（满足其一）：
        //  a. 声明了文件系统能力位（READ / WRITE）—— 本地自建工具的常规路径；
        //  b. 未声明且来源为 MCP / 脚本 —— 协议层无法声明路径参数，
        //     默认按"可能访问任意路径"处理，抽取不到即降级为确认，绝不静默放行。
        //
        // 不进入校验：本地工具且未声明能力位（= 明确不碰文件系统），
        // 避免"参数里恰好有个像路径的字符串"造成大量误报。
        FileSystemAccess access = metadata.getFilesystemAccess();
        boolean declared = access != null && access != FileSystemAccess.NONE;
        boolean unDeclaredUnDeclarable = !declared && isUnDeclarableSource(metadata);

        if (!declared && !unDeclaredUnDeclarable) {
            return null;
        }

        WorkspaceScope scope = context == null ? null : context.getWorkspaceScope();

        // 2. 路径获取
        List<PathAccess> accesses = new ArrayList<>();
        boolean uncertain = false;
        String uncertainReason = null;
        boolean hasPathDeclaration = CollectionUtils.isNotEmpty(metadata.getPathParams());

        if (hasPathDeclaration) {
            // 2.1 已声明：精确提取
            accesses.addAll(extractDeclared(metadata.getPathParams(), params));
        } else {
            // 2.2 未声明（MCP / 脚本 / 命令类）：模型抽取
            PathExtractor.ExtractionResult extraction =
                    pathExtractor.extract(metadata.getName(), metadata.getDescription(), params);
            if (extraction.isUncertain()) {
                // 抽取不到 ≠ 放行，而是降级为需要确认（D13）
                uncertain = true;
                uncertainReason = extraction.getUncertainReason();
            }
            accesses.addAll(extraction.getPaths());
        }

        // 无路径参数声明的工具（MCP / 脚本 / 命令类），若既抽不到路径也不确定
        // （抽取器被禁用时会发生），同样按确认处理 —— 抽取不到 ≠ 放行（D13）
        if (!hasPathDeclaration && accesses.isEmpty() && !uncertain) {
            uncertain = true;
            uncertainReason = processDeclareHint(metadata);
        }

        // 无路径访问事实，且不不确定 → 放行
        if (accesses.isEmpty() && !uncertain) {
            return null;
        }

        String conversationCode = context == null ? null : context.getConversationCode();

        // 3. 逐路径判定，聚合结果
        List<PathVerdict> verdicts = new ArrayList<>();
        for (PathAccess pathAccess : accesses) {
            PathVerdict verdict = pathGuard.check(pathAccess, scope, authorizationStore, conversationCode);
            if (verdict.isDenied()) {
                // 高危路径：直接拒绝，不进入确认流程（用户授权也不解除）
                log.warn("Path access denied (protected): tool={}, path={}", metadata.getName(), pathAccess.getRawPath());
                return ToolExecutionResult.fail(ToolExecutionResult.ERROR_PATH_PROTECTED, verdict.getReason());
            }
            // 3.1 用户消息授权（D9）：用户在消息中明确写出的路径，视为已授权，不再弹确认
            if (verdict.isNeedConfirm() && isAuthorizedByUserMessage(context, verdict)) {
                verdict = PathVerdict.builder()
                        .decision(PathGuard.Decision.ALLOW_AUTHORIZED)
                        .rawPath(verdict.getRawPath())
                        .normalizedPath(verdict.getNormalizedPath())
                        .direction(verdict.getDirection())
                        .reason("用户在消息中明确指定了该路径")
                        .build();
            }
            verdicts.add(verdict);
        }

        List<PathAccess> needConfirm = new ArrayList<>();
        for (PathVerdict verdict : verdicts) {
            if (verdict.isNeedConfirm()) {
                needConfirm.add(PathAccess.builder()
                        .paramName(null)
                        .rawPath(String.valueOf(verdict.getNormalizedPath() == null
                                ? verdict.getRawPath() : verdict.getNormalizedPath()))
                        .direction(verdict.getDirection())
                        .source(PathAccess.SOURCE_DECLARED)
                        .build());
            }
        }

        // 抽取不确定时，把原始路径一并纳入确认（展示给用户）
        if (uncertain && needConfirm.isEmpty()) {
            for (PathAccess pathAccess : accesses) {
                needConfirm.add(pathAccess);
            }
        }

        // 全部放行
        if (needConfirm.isEmpty() && !uncertain) {
            return null;
        }

        // 4. 确认交互（阻塞，超时按拒绝）
        if (confirmer == null) {
            log.warn("Path access requires confirmation but no confirmer configured, deny: tool={}", metadata.getName());
            return ToolExecutionResult.fail(ToolExecutionResult.ERROR_PATH_OUT_OF_WORKSPACE,
                    buildOutOfWorkspaceMessage(scope, needConfirm));
        }

        PathAccessConfirmer.ConfirmContext confirmContext = buildConfirmContext(context);
        if (StringUtils.isBlank(confirmContext.getConversationCode())) {
            // 没有会话上下文（如定时任务），无法交互确认 → 拒绝
            log.warn("Path access requires confirmation but no conversation context, deny: tool={}", metadata.getName());
            return ToolExecutionResult.fail(ToolExecutionResult.ERROR_PATH_OUT_OF_WORKSPACE,
                    buildOutOfWorkspaceMessage(scope, needConfirm));
        }

        String reason = uncertain ? uncertainReason : null;
        PathAccessConfirmer.ConfirmResult confirmResult = confirmer.confirm(
                metadata.getName() + " 请求访问工作空间外的文件",
                needConfirm, reason, confirmContext);

        if (confirmResult == null || !confirmResult.isAllowed()) {
            log.info("Path access denied by user: tool={}, paths={}", metadata.getName(), needConfirm);
            return ToolExecutionResult.fail(ToolExecutionResult.ERROR_PATH_OUT_OF_WORKSPACE,
                    buildOutOfWorkspaceMessage(scope, needConfirm));
        }

        // 5. 会话级授权：记住该目录，避免重复询问
        if (confirmResult.getDecision() == PathAccessConfirmer.ConfirmResult.Decision.SESSION_DIR
                && authorizationStore != null
                && properties.isSessionAuthorizationEnabled()) {
            for (PathAccess pathAccess : needConfirm) {
                try {
                    Path dir = Paths.get(pathAccess.getRawPath());
                    Path target = Files.isDirectory(dir) ? dir : dir.getParent();
                    if (target != null) {
                        authorizationStore.grantDirectory(confirmContext.getConversationCode(), target);
                    }
                } catch (Exception e) {
                    log.warn("Failed to grant session authorization for path: {}", pathAccess.getRawPath(), e);
                }
            }
        }

        return null;
    }

    /**
     * 是否为"协议层无法声明路径参数"的工具来源（MCP / 脚本）。
     * <p>
     * 这类工具声明 NONE 表示"无法声明"而非"不碰文件系统"，须按从严处理（D13）。
     */
    private boolean isUnDeclarableSource(ToolMetadata metadata) {
        String sourceType = metadata.getSourceType();
        if (StringUtils.isBlank(sourceType)) {
            return false;
        }
        return SourceType.MCP.equalsIgnoreCase(sourceType)
                || SourceType.SCRIPT.equalsIgnoreCase(sourceType);
    }

    private String processDeclareHint(ToolMetadata metadata) {
        return "工具 " + metadata.getName() + " 未声明路径参数，无法确认其文件访问范围";
    }

    /**
     * 判断该路径是否由用户在消息中明确指定（D9）。
     * <p>
     * 注意：这里只做"文本中出现过该路径"的比对，<b>不猜测用户意图</b>。
     * 用户没有明确写出的路径不会授权，仍走确认流程。
     */
    private boolean isAuthorizedByUserMessage(ToolContext context, PathVerdict verdict) {
        if (userPathMatcher == null || context == null || verdict == null) {
            return false;
        }
        String userContent = context.getUserContent();
        if (StringUtils.isBlank(userContent)) {
            return false;
        }
        Path target = verdict.getNormalizedPath();
        if (target == null) {
            return false;
        }
        try {
            return userPathMatcher.isAuthorizedByUserMessage(target, userContent);
        } catch (Exception e) {
            log.warn("User message path authorization check failed, fallback to confirm: path={}", target, e);
            return false;
        }
    }

    /**
     * 按声明提取路径参数（支持多个路径、方向各不相同）。
     */
    private List<PathAccess> extractDeclared(List<ToolPathParam> decls, Map<String, Object> params) {
        List<PathAccess> accesses = new ArrayList<>();
        if (params == null) {
            return accesses;
        }
        for (ToolPathParam decl : decls) {
            Object value = params.get(decl.getName());
            if (value == null) {
                continue;
            }
            if (value instanceof Iterable<?> iterable) {
                for (Object item : iterable) {
                    if (item != null && StringUtils.isNotBlank(item.toString())) {
                        accesses.add(toAccess(decl, item.toString()));
                    }
                }
            } else if (StringUtils.isNotBlank(value.toString())) {
                accesses.add(toAccess(decl, value.toString()));
            }
        }
        return accesses;
    }

    private PathAccess toAccess(ToolPathParam decl, String rawPath) {
        return PathAccess.builder()
                .paramName(decl.getName())
                .rawPath(rawPath)
                .direction(decl.getDirection())
                .kind(decl.getKind())
                .source(PathAccess.SOURCE_DECLARED)
                .build();
    }

    private PathAccessConfirmer.ConfirmContext buildConfirmContext(ToolContext context) {
        PathAccessConfirmer.ConfirmContext confirmContext = new PathAccessConfirmer.ConfirmContext();
        if (context != null) {
            confirmContext.setConversationCode(context.getConversationCode());
            confirmContext.setAgentCode(context.getAgentCode());
            confirmContext.setUserCode(context.getUserCode());
        }
        return confirmContext;
    }

    /**
     * 构建给 LLM 的可执行引导文案（不能只说"权限不足"）。
     */
    private String buildOutOfWorkspaceMessage(WorkspaceScope scope, List<PathAccess> accesses) {
        String workspace = scope == null || scope.isEmpty() ? "(未配置)" : scope.getPrimaryWorkspace();
        String paths = accesses.stream()
                .map(PathAccess::getRawPath)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        return "路径访问被拒绝：目标路径 [%s] 不在你的工作空间 [%s] 内，且未获得用户授权。"
                .formatted(paths, workspace)
                + "请将文件放到工作空间的 output 目录下，或先向用户说明并请求授权后重试。";
    }
}

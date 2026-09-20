package com.xiaomizhou.dpsk.tool.workspace;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 工作空间配置。
 * <p>
 * 由 {@code @EnableConfigurationProperties(WorkspaceProperties.class)} 注册并完成属性绑定。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/9/20
 */
@Data
@Slf4j
@ConfigurationProperties(prefix = "com.xiaomizhou.dpsk.opc.workspace")
public class WorkspaceProperties {

    /**
     * 工作空间根目录：实体未配置 workspace 时使用 {@code <root>/<实体编码>}。
     * <p>
     * 建议指向用户数据区（如 {@code ~/.yoo/workspace}），不要指向安装目录或系统目录。
     */
    private String root = "";

    /**
     * 是否启用工作空间边界校验。
     */
    private boolean enabled = true;

    /**
     * 是否启用高危路径拦截（即使工作空间未配置也生效）。
     */
    private boolean protectedPathEnabled = true;

    /**
     * 额外的高危路径正则（逗号分隔）。
     */
    private List<String> protectedPathPatterns = new ArrayList<>();

    /**
     * 启动时是否自动创建标准目录结构（output / tmp / scripts）。
     */
    private boolean autoCreateStructure = false;

    /**
     * 用户授权的外部路径是否在会话内有效（true=会话级，false=仅单次）。
     */
    private boolean sessionAuthorizationEnabled = true;

    /**
     * 推导某实体的默认工作空间：{@code <root>/<ownerCode>}。
     * <p>
     * 根目录未配置时回退到用户主目录下的 {@code <user.home>/.dpsk/workspace}，
     * 保证任何情况下都能得到一个可用的默认工作空间——否则新建的 Agent 在重启前
     * （尚未被启动期回填）会因 workspace 为空而拿不到文件读写边界。
     *
     * @param ownerCode 实体编码（Agent / 群）
     * @return 默认工作空间路径；ownerCode 为空时返回空串
     */
    public String resolveDefault(String ownerCode) {
        if (StringUtils.isBlank(ownerCode)) {
            return "";
        }
        Path base = rootPath();
        if (base == null) {
            base = fallbackRoot();
            log.debug("Workspace root not configured, fallback to '{}'", base);
        }
        return base.resolve(ownerCode).toString();
    }

    /**
     * 解析最终工作空间：用户配置优先，否则用默认值。
     * <p>
     * <b>不依赖数据库回填</b>：即使实体配置为空（如新建后未重启），
     * 此处也会实时推导出默认工作空间，保证运行期行为与配置页展示一致。
     */
    public String resolve(String configured, String ownerCode) {
        if (StringUtils.isNotBlank(configured)) {
            return configured.trim();
        }
        return resolveDefault(ownerCode);
    }

    /**
     * 根目录未配置时的兜底根目录：{@code <user.home>/.dpsk/workspace}。
     */
    private Path fallbackRoot() {
        return Paths.get(System.getProperty("user.home", "."), ".dpsk", "workspace");
    }

    public Path rootPath() {
        return StringUtils.isBlank(root) ? null : Paths.get(root);
    }
}

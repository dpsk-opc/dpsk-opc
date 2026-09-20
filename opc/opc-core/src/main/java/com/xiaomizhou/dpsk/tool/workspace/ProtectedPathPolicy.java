package com.xiaomizhou.dpsk.tool.workspace;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 高危路径策略。
 * <p>
 * 命中即<b>直接拒绝</b>，不进入确认流程 —— 这不是"用户意图"问题，而是系统底线问题，
 * 不能通过用户确认解除。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/9/20
 */
@Slf4j
public class ProtectedPathPolicy {

    /**
     * 内置高危路径模式（正则，匹配规范化后的小写绝对路径，分隔符统一为 /）。
     */
    private static final List<Pattern> DEFAULT_PATTERNS = new ArrayList<>();

    static {
        // ---------- Windows ----------
        // 系统目录
        DEFAULT_PATTERNS.add(Pattern.compile("^[a-z]:/windows(/.*)?$"));
        DEFAULT_PATTERNS.add(Pattern.compile("^[a-z]:/program files(\\(x86\\))?(/.*)?$"));
        DEFAULT_PATTERNS.add(Pattern.compile("^[a-z]:/programdata(/.*)?$"));
        DEFAULT_PATTERNS.add(Pattern.compile("^[a-z]:/system volume information(/.*)?$"));
        DEFAULT_PATTERNS.add(Pattern.compile("^[a-z]:/\\$recycle\\.bin(/.*)?$"));
        DEFAULT_PATTERNS.add(Pattern.compile("^[a-z]:/recovery(/.*)?$"));
        // 用户目录下的敏感位置
        DEFAULT_PATTERNS.add(Pattern.compile("^[a-z]:/users/[^/]+/\\.ssh(/.*)?$"));
        DEFAULT_PATTERNS.add(Pattern.compile("^[a-z]:/users/[^/]+/\\.aws(/.*)?$"));
        DEFAULT_PATTERNS.add(Pattern.compile("^[a-z]:/users/[^/]+/\\.gnupg(/.*)?$"));
        DEFAULT_PATTERNS.add(Pattern.compile("^[a-z]:/users/[^/]+/\\.kube(/.*)?$"));
        DEFAULT_PATTERNS.add(Pattern.compile("^[a-z]:/users/[^/]+/appdata/(roaming|local)/google/chrome(/.*)?$"));
        DEFAULT_PATTERNS.add(Pattern.compile("^[a-z]:/users/[^/]+/appdata/(roaming|local)/microsoft/(credentials|protect)(/.*)?$"));
        DEFAULT_PATTERNS.add(Pattern.compile("^[a-z]:/users/[^/]+/ntuser\\.dat$"));
        // 凭据 / 密钥类文件
        DEFAULT_PATTERNS.add(Pattern.compile(".*/(id_rsa|id_ed25519|id_ecdsa)$"));
        DEFAULT_PATTERNS.add(Pattern.compile(".*\\.(pem|pfx|p12|jks|keystore)$"));
        DEFAULT_PATTERNS.add(Pattern.compile(".*/credentials(\\.json)?$"));
        DEFAULT_PATTERNS.add(Pattern.compile("^/etc/shadow$"));

        // ---------- Unix / macOS ----------
        DEFAULT_PATTERNS.add(Pattern.compile("^/(bin|sbin|usr/bin|usr/sbin|usr/lib|boot|dev|proc|sys)(/.*)?$"));
        DEFAULT_PATTERNS.add(Pattern.compile("^/etc(/.*)?$"));
        DEFAULT_PATTERNS.add(Pattern.compile("^/var/lib(/.*)?$"));
        DEFAULT_PATTERNS.add(Pattern.compile("^/system(/.*)?$"));
        DEFAULT_PATTERNS.add(Pattern.compile("^/library/(launchdaemons|launchagents|preferences)(/.*)?$"));
        DEFAULT_PATTERNS.add(Pattern.compile("^/(root|home/[^/]+)/\\.ssh(/.*)?$"));
        DEFAULT_PATTERNS.add(Pattern.compile("^/(root|home/[^/]+)/\\.aws(/.*)?$"));
        DEFAULT_PATTERNS.add(Pattern.compile("^/(root|home/[^/]+)/\\.gnupg(/.*)?$"));
        DEFAULT_PATTERNS.add(Pattern.compile("^/private/etc(/.*)?$"));
        // 敏感文件直配
        DEFAULT_PATTERNS.add(Pattern.compile("^/(private/)?etc/(passwd|shadow|sudoers)$"));
    }

    /**
     * 额外配置的高危模式（来自配置项，逗号分隔的正则）。
     */
    private final List<Pattern> extraPatterns = new ArrayList<>();

    /**
     * 是否启用高危拦截。
     */
    private final boolean enabled;

    public ProtectedPathPolicy(boolean enabled, List<String> extraPatterns) {
        this.enabled = enabled;
        if (extraPatterns != null) {
            for (String p : extraPatterns) {
                if (StringUtils.isBlank(p)) {
                    continue;
                }
                try {
                    this.extraPatterns.add(Pattern.compile(p));
                } catch (Exception e) {
                    log.warn("Invalid protected path pattern, ignored: {}", p, e);
                }
            }
        }
    }

    public static ProtectedPathPolicy defaultPolicy() {
        return new ProtectedPathPolicy(true, null);
    }

    /**
     * 判定路径是否为高危路径。
     *
     * @param normalizedPath 规范化后的绝对路径（已解析符号链接、已统一分隔符）
     * @return 命中返回 true
     */
    public boolean isProtected(Path normalizedPath) {
        if (!enabled || normalizedPath == null) {
            return false;
        }
        String normalized = normalize(normalizedPath.toString());
        for (Pattern p : DEFAULT_PATTERNS) {
            if (p.matcher(normalized).matches()) {
                return true;
            }
        }
        for (Pattern p : extraPatterns) {
            if (p.matcher(normalized).matches()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 统一分隔符为 /、转小写，便于正则匹配。
     */
    private String normalize(String path) {
        return path.replace('\\', '/').toLowerCase(Locale.ROOT);
    }
}

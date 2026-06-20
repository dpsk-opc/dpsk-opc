package com.xiaomizhou.dpsk.tool;

import com.xiaomizhou.dpsk.tool.model.ToolMetadata;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 命令安全检测器 —— 三层策略级联检查。
 *
 * <h3>设计理念</h3>
 * <pre>
 *   工具本身无所谓危险不危险，关键是看执行什么命令、传了什么参数。
 *   例如：cat xxx.log 是安全的，但 cat xxx.key 就是危险的。
 *
 *   三层检查策略：
 *   第一层：工具风险等级 —— 工具是否标记为危险（仅危险工具才进入后续检查）
 *   第二层：命令黑名单 —— 命令本身是否在危险模式列表中（如 rm -rf /, mkfs, shutdown）
 *   第三层：参数安全 —— 命令本身安全，但参数指向敏感文件/路径（如 cat *.key, ls /etc/shadow）
 * </pre>
 *
 * <h3>检查结果</h3>
 * <pre>
 *   BLOCKED   — 命中黑名单，直接拒绝，不允许执行
 *   CONFIRM   — 命中敏感参数规则，需要用户确认
 *   SAFE      — 通过所有检查，可以执行
 * </pre>
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/19
 */
@Slf4j
public class CommandSafetyChecker {

    // ==================== 第零层：关键字直检（兜底策略） ====================
    // 不论命令如何包装/嵌套/编码，只要原始字符串中包含这些关键字，
    // 直接 BLOCKED。这是最底层的防线，不依赖解包和正则。

    /**
     * BLOCKED 关键字 —— 原始字符串包含任一关键字则直接拒绝。
     * 注意：关键字需要足够特异，避免误杀正常命令。
     */
    private static final List<String> BLOCKED_KEYWORDS = List.of(
            // 删根操作
            "rm -rf /",
            "rm -rf /*",
            "rm -rf --no-preserve-root",
            // 格式化磁盘
            "mkfs.",
            "mke2fs",
            // 覆写磁盘
            "dd if=",
            "of=/dev/sd",
            "of=/dev/nvme",
            "of=/dev/hd",
            "of=/dev/xvd",
            // 系统级危险操作
            "shutdown",
            "reboot",
            "halt",
            "poweroff",
            // Fork 炸弹
            ":( ) { :|:& }; :",
            ":(){ :|:& };:",
            // 防火墙关闭
            "iptables -F",
            "ufw disable",
            // chmod 777 根目录
            "chmod -R 777 /",
            "chmod 777 /"
    );

    /**
     * CONFIRM 关键字 —— 原始字符串包含任一关键字则需要用户确认。
     */
    private static final List<String> CONFIRM_KEYWORDS = List.of(
            // 敏感系统文件
            "/etc/shadow",
            "/etc/sudoers",
            "/etc/ssl/private",
            "/root/.ssh",
            // 密钥/证书文件后缀
            ".key",
            ".pem",
            ".p12",
            ".pfx",
            ".jks",
            ".keystore",
            // 数据库密码
            "--password=",
            // 敏感环境变量
            "LD_PRELOAD",
            // 反弹shell
            "nc -l",
            "nc -e",
            "ncat -e",
            // 强制杀进程
            "kill -9",
            "killall -9",
            "pkill -9"
    );

    /**
     * 关键字直检 —— 对原始命令字符串做简单的 contains 匹配。
     * 不依赖正则、不依赖解包，直接拦截或要求确认。
     *
     * @return null 表示未命中关键字，否则返回对应的 SafetyResult
     */
    private static SafetyResult checkKeywords(String raw) {
        String lower = raw.toLowerCase();

        // BLOCKED 关键字优先
        for (String keyword : BLOCKED_KEYWORDS) {
            if (lower.contains(keyword.toLowerCase())) {
                log.warn("[Safety-L0-BLOCKED] Keyword hit: '{}', raw='{}'", keyword, truncate(raw));
                return SafetyResult.blocked("命中危险关键字: " + keyword);
            }
        }

        // CONFIRM 关键字
        for (String keyword : CONFIRM_KEYWORDS) {
            if (lower.contains(keyword.toLowerCase())) {
                log.warn("[Safety-L0-CONFIRM] Keyword hit: '{}', raw='{}'", keyword, truncate(raw));
                return SafetyResult.confirm("命中敏感关键字: " + keyword);
            }
        }

        return null;
    }

    // ==================== 第二层：命令黑名单 ====================
    // 命中则直接 BLOCKED，不进入确认流程

    private static final List<Pattern> COMMAND_BLACKLIST = List.of(
            // 删根 / 格式化
            // 匹配: rm -rf /, rm -rf /*, rm -rf / *, rm -rf /home, rm -rf / --no-preserve-root
            Pattern.compile("\\brm\\s+-rf\\s+/"),
            Pattern.compile("\\brm\\s+-rf\\s+--no-preserve-root\\b"),
            Pattern.compile("\\bmkfs\\.", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bdd\\s+if="),
            // 磁盘覆写
            Pattern.compile(">\\s*/dev/sd[a-z]"),
            Pattern.compile(">\\s*/dev/nvme"),
            Pattern.compile(">\\s*/dev/hd[a-z]"),
            Pattern.compile(">\\s*/dev/xvd[a-z]"),
            // 系统级危险操作
            Pattern.compile("\\bshutdown\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\breboot\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bhalt\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bpoweroff\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\binit\\s+[0-6]\\b"),
            // Fork 炸弹
            Pattern.compile(":\\(\\)\\s*\\{\\s*:\\|:&\\s*\\}\\s*;\\s*:"),
            // 远程代码执行
            Pattern.compile("\\bwget\\s+.*\\|\\s*(sh|bash)\\b"),
            Pattern.compile("\\bcurl\\s+.*\\|\\s*(sh|bash)\\b"),
            // 防火墙操作
            Pattern.compile("\\biptables\\s+-F\\b"),
            Pattern.compile("\\bufw\\s+disable\\b"),
            Pattern.compile("\\bfirewall-cmd\\s+.*--permanent\\b"),
            // chmod 777 危险路径
            Pattern.compile("\\bchmod\\s+-R\\s+777\\s+/"),
            Pattern.compile("\\bchmod\\s+777\\s+/"),
            // 递归删除系统目录
            Pattern.compile("\\brm\\s+-rf\\s+/(etc|bin|boot|dev|lib|proc|sys|usr|var)(\\b|/)")
    );

    // ==================== 第三层：敏感参数规则 ====================
    // 命令本身可能无害，但参数指向敏感资源，命中则 CONFIRM

    /**
     * 敏感参数规则：检查命令+参数组合是否危险。
     * 每条规则包含一个 Pattern 和对应的说明。
     */
    private static final List<SensitiveParamRule> SENSITIVE_PARAM_RULES = List.of(
            // --- 敏感文件读取 ---
            // cat/tail/head/less/more 读取敏感文件
            SensitiveParamRule.of(
                    "\\b(cat|tail|head|less|more|nl|od|strings)\\b.*\\b(/etc/shadow|/etc/passwd|/etc/sudoers|/root/|/var/log/auth\\.log)",
                    "读取敏感系统文件",
                    false
            ),
            // cat 读取密钥/证书/凭证文件
            SensitiveParamRule.of(
                    "\\bcat\\b.*\\.(key|pem|crt|cer|p12|pfx|jks|keystore|p12|pkcs12)(\\s|$|\"|')",
                    "读取密钥/证书文件",
                    true
            ),
            // grep/find 搜索敏感内容
            SensitiveParamRule.of(
                    "\\b(grep|rg|ag|ack)\\b.*(password|secret|token|api[_-]?key|credential)",
                    "搜索敏感关键词",
                    true
            ),
            // --- 敏感文件写入 ---
            // echo/cat/tee 写入系统配置目录
            SensitiveParamRule.of(
                    "\\b(echo|cat|tee|printf)\\b.*>\\s*(/etc/|/usr/local/etc/|/opt/)",
                    "写入系统配置目录",
                    false
            ),
            // 追加写入系统目录
            SensitiveParamRule.of(
                    "\\b(echo|cat|tee|printf)\\b.*>>\\s*(/etc/|/usr/local/etc/)",
                    "追加写入系统配置目录",
                    false
            ),
            // --- 敏感路径访问 ---
            // ls/find/stat 查看敏感目录
            SensitiveParamRule.of(
                    "\\b(ls|find|stat|du|df|tree)\\b.*(/etc/shadow|/etc/ssl/private|/root/\\.ssh|/var/lib/mysql)",
                    "查看敏感目录",
                    false
            ),
            // --- 进程/服务操作 ---
            // kill/killall 发送信号
            SensitiveParamRule.of(
                    "\\b(kill|killall|pkill)\\s+-9\\b",
                    "强制终止进程",
                    false
            ),
            // systemctl/service 操作核心服务
            SensitiveParamRule.of(
                    "\\b(systemctl|service)\\b.*\\b(stop|disable|mask)\\b.*\\b(sshd|firewalld|iptables|docker|nginx|httpd|mysql|postgresql)\\b",
                    "停用核心服务",
                    false
            ),
            // --- 网络操作 ---
            // curl/wget 下载并执行
            SensitiveParamRule.of(
                    "\\b(curl|wget)\\b.*\\b-o\\b|\\b(curl|wget)\\b.*\\b-O\\b.*(/etc/|/usr/local/bin|/usr/bin|/bin/)",
                    "下载文件到系统目录",
                    false
            ),
            // nc/ncat 建立监听/连接
            SensitiveParamRule.of(
                    "\\b(nc|ncat|netcat)\\b.*\\b(-l|--listen|-e|--exec)\\b",
                    "建立网络监听/反弹shell",
                    false
            ),
            // --- 权限修改 ---
            // chmod 修改系统文件权限
            SensitiveParamRule.of(
                    "\\bchmod\\b.*\\b(u\\+s|g\\+s|\\+s)\\b",
                    "设置SUID/SGID位",
                    false
            ),
            // chown 修改系统文件所有者
            SensitiveParamRule.of(
                    "\\bchown\\b.*\\b(root:|:root)\\b.*(/home/|/tmp/|/var/www/)",
                    "修改文件所有者为root",
                    false
            ),
            // --- 环境/配置修改 ---
            // 修改 profile/bashrc
            SensitiveParamRule.of(
                    "\\b(echo|cat|tee|printf)\\b.*>>\\s*(/etc/profile|/etc/bashrc|~/\\.bashrc|~/\\.bash_profile|~/\\.profile)",
                    "修改Shell配置文件",
                    false
            ),
            // export 敏感环境变量
            SensitiveParamRule.of(
                    "\\bexport\\b.*\\b(LD_PRELOAD|LD_LIBRARY_PATH|PATH)\\s*=",
                    "修改敏感环境变量",
                    false
            ),
            // --- 数据库操作 ---
            // mysql/psql 带密码执行
            SensitiveParamRule.of(
                    "\\b(mysql|psql|mongo)\\b.*\\b-p\\s*\\S+",
                    "命令行明文传数据库密码",
                    true
            ),
            // mysqldump/pg_dump 导出数据
            SensitiveParamRule.of(
                    "\\b(mysqldump|pg_dump|mongodump)\\b.*\\b--password=\\S+",
                    "数据库导出含明文密码",
                    true
            )
    );

    // ==================== 命令解包：循环剥离 shell 包装器 ====================

    /**
     * Shell 包装器模式 —— 匹配外层 cmd / bash / sh / pwsh 等调用。
     * <p>
     * 攻击者可能使用多层包装来绕过检查，例如：
     * <pre>
     *   cmd /c "bash -c 'cat /etc/shadow'"   → 需要循环剥离
     *   sh -c "sh -c 'rm -rf /'"             → 嵌套 sh -c
     * </pre>
     * 因此解包采用循环方式，持续剥离直到不再变化。
     */
    private static final Pattern SHELL_WRAPPER = Pattern.compile(
            "\\b(cmd|command\\.com)\\s+/[cCkK]\\s*",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern BASH_WRAPPER = Pattern.compile(
            "\\b(bash|sh|zsh|dash|ksh)\\s+-c\\s*",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern POWERSHELL_WRAPPER = Pattern.compile(
            "\\b(pwsh|powershell(\\.exe)?)\\s+-(Command|c|EncodedCommand|e|ec)\\s*",
            Pattern.CASE_INSENSITIVE
    );

    /** 防止无限循环的最大解包次数 */
    private static final int MAX_UNWRAP_DEPTH = 5;

    /**
     * 循环剥离命令外层的 shell 包装器，直到露出核心命令。
     * <p>
     * 每次循环：
     * 1. 尝试匹配 shell 包装器前缀（cmd /c, bash -c, powershell -Command）
     * 2. 匹配到则截取后面的命令部分
     * 3. 去掉首尾引号
     * 4. 重复直到没有包装器或达到最大深度
     */
    static String unwrapCommand(String command) {
        if (command == null || command.isBlank()) {
            return command;
        }
        String prev;
        String cur = command.trim();

        for (int depth = 0; depth < MAX_UNWRAP_DEPTH; depth++) {
            prev = cur;

            // 尝试剥离各种 shell 包装器
            cur = tryUnwrapSingle(cur);

            // 没有变化则停止
            if (cur.equals(prev)) {
                break;
            }
        }
        return cur;
    }

    /**
     * 尝试剥离一层 shell 包装器。
     * 返回剥离后的命令；如果没有匹配到任何包装器，返回原值。
     */
    private static String tryUnwrapSingle(String cmd) {
        java.util.regex.Matcher m;

        // 剥离 cmd /c /k 包装
        m = SHELL_WRAPPER.matcher(cmd);
        if (m.find()) {
            return stripQuotes(cmd.substring(m.end()).trim());
        }

        // 剥离 bash/sh/zsh -c 包装
        m = BASH_WRAPPER.matcher(cmd);
        if (m.find()) {
            return stripQuotes(cmd.substring(m.end()).trim());
        }

        // 剥离 powershell 包装
        m = POWERSHELL_WRAPPER.matcher(cmd);
        if (m.find()) {
            return stripQuotes(cmd.substring(m.end()).trim());
        }

        // 没有包装器，但可能整体被引号包裹（如 "rm -rf /"）
        return stripQuotes(cmd);
    }

    /**
     * 去掉首尾匹配的引号（双引号或单引号）。
     */
    private static String stripQuotes(String s) {
        if (s == null || s.length() < 2) {
            return s;
        }
        char first = s.charAt(0);
        char last = s.charAt(s.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }


    public static SafetyResult check(String command) {
        if (command == null || command.isBlank()) {
            return SafetyResult.safe();
        }

        String raw = command.trim();

        // 第零层：关键字直检（兜底策略，对原始字符串做 contains 匹配）
        SafetyResult keywordResult = checkKeywords(raw);
        if (keywordResult != null) {
            return keywordResult;
        }

        // 先解包，再对原始命令和解包后的命令各做一次检查
        String unwrapped = unwrapCommand(raw);
        String cmd = unwrapped.equals(raw) ? raw : raw + " [unwrapped: " + unwrapped + "]";

        // 第二层：命令黑名单检查 → BLOCKED（对原始和解包后都检查）
        SafetyResult blacklistResult = checkBlacklist(raw);
        if (blacklistResult != null) {
            log.warn("[Safety-L2-BLOCKED] Command matched blacklist: reason='{}', raw='{}', unwrapped='{}'",
                    blacklistResult.getReason(), truncate(raw), truncate(unwrapped));
            return blacklistResult;
        }
        if (!unwrapped.equals(raw)) {
            blacklistResult = checkBlacklist(unwrapped);
            if (blacklistResult != null) {
                log.warn("[Safety-L2-BLOCKED] Unwrapped command matched blacklist: reason='{}', raw='{}', unwrapped='{}'",
                        blacklistResult.getReason(), truncate(raw), truncate(unwrapped));
                return blacklistResult;
            }
        }

        // 第三层：敏感参数检查 → CONFIRM（对原始和解包后都检查）
        SafetyResult sensitiveResult = checkSensitiveParams(raw);
        if (sensitiveResult != null) {
            log.warn("[Safety-L3-CONFIRM] Command matched sensitive rule: reason='{}', raw='{}'",
                    sensitiveResult.getReason(), truncate(raw));
            return sensitiveResult;
        }
        if (!unwrapped.equals(raw)) {
            sensitiveResult = checkSensitiveParams(unwrapped);
            if (sensitiveResult != null) {
                log.warn("[Safety-L3-CONFIRM] Unwrapped command matched sensitive rule: reason='{}', raw='{}', unwrapped='{}'",
                        sensitiveResult.getReason(), truncate(raw), truncate(unwrapped));
                return sensitiveResult;
            }
        }

        log.debug("[Safety] Command passed all checks: '{}'", truncate(cmd));
        return SafetyResult.safe();
    }

    // ==================== 公共 API ====================

    /**
     * 检查命令安全性。
     *
     * @param toolName 工具名称（如 "execute_command"）
     * @param metadata 工具元数据（可 null，为 null 时跳过第一层检查）
     * @param command  完整命令行字符串
     * @return 检查结果
     */
    public static SafetyResult check(String toolName, ToolMetadata metadata, String command) {
        // 第一层：工具风险等级检查
        if (metadata != null) {
            if (!metadata.isDangerous()) {
                log.debug("[Safety-L1] Tool '{}' is not dangerous, skip further checks.", toolName);
                return SafetyResult.safe();
            }
            log.debug("[Safety-L1] Tool '{}' riskLevel={}, proceed to L2/L3 checks.",
                    toolName, metadata.getRiskLevel());
        }

        return check(command);
    }

    private static SafetyResult checkBlacklist(String cmd) {
        for (Pattern pattern : COMMAND_BLACKLIST) {
            if (pattern.matcher(cmd).find()) {
                return SafetyResult.blocked("命中命令黑名单: " + pattern.pattern());
            }
        }
        return null;
    }

    private static SafetyResult checkSensitiveParams(String cmd) {
        for (SensitiveParamRule rule : SENSITIVE_PARAM_RULES) {
            if (rule.pattern.matcher(cmd).find()) {
                return SafetyResult.confirm(rule.reason);
            }
        }
        return null;
    }

    /**
     * 仅做第二、三层检查（不检查工具风险等级）。
     * 用于工具本身不危险，但需要对特定参数做检查的场景。
     */
    public SafetyResult checkCommandOnly(String command) {
        return check(null, null, command);
    }

    private static String truncate(String s) {
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }

    // ==================== 内部类型 ====================

    /**
     * 安全检测结果。
     */
    public enum SafetyLevel {
        /** 安全，可以执行 */
        SAFE,
        /** 需要用户确认 */
        CONFIRM,
        /** 命中黑名单，直接拒绝 */
        BLOCKED
    }

    /**
     * 安全检测结果对象。
     */
    public static class SafetyResult {
        private final SafetyLevel level;
        private final String reason;

        private SafetyResult(SafetyLevel level, String reason) {
            this.level = level;
            this.reason = reason;
        }

        public static SafetyResult safe() {
            return new SafetyResult(SafetyLevel.SAFE, null);
        }

        public static SafetyResult confirm(String reason) {
            return new SafetyResult(SafetyLevel.CONFIRM, reason);
        }

        public static SafetyResult blocked(String reason) {
            return new SafetyResult(SafetyLevel.BLOCKED, reason);
        }

        public boolean isSafe() {
            return level == SafetyLevel.SAFE;
        }

        public boolean needsConfirmation() {
            return level == SafetyLevel.CONFIRM;
        }

        public boolean isBlocked() {
            return level == SafetyLevel.BLOCKED;
        }

        public SafetyLevel getLevel() {
            return level;
        }

        public String getReason() {
            return reason;
        }

        @Override
        public String toString() {
            return "SafetyResult{level=" + level + ", reason='" + reason + "'}";
        }
    }

    /**
     * 敏感参数规则。
     * <p>
     * isConfirmationOnly 标记该规则是否仅需确认（而非直接拦截），当前所有 L3 规则都是确认级别。
     */
    private static class SensitiveParamRule {
        final Pattern pattern;
        final String reason;
        final boolean confirmationOnly; // 预留：未来可区分 BLOCKED vs CONFIRM

        private SensitiveParamRule(Pattern pattern, String reason, boolean confirmationOnly) {
            this.pattern = pattern;
            this.reason = reason;
            this.confirmationOnly = confirmationOnly;
        }

        static SensitiveParamRule of(String regex, String reason, boolean confirmationOnly) {
            return new SensitiveParamRule(
                    Pattern.compile(regex, Pattern.CASE_INSENSITIVE),
                    reason,
                    confirmationOnly
            );
        }
    }
}

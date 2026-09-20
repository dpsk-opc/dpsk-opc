package com.xiaomizhou.dpsk.tool.workspace;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 用户消息路径授权匹配器（对应 D9）。
 * <p>
 * 语义：<b>用户在消息中明确给出某路径时，视为对该路径的用户授权</b>，
 * 此时工具访问该路径不应弹确认框（产品方案 3.1 场景 2）。
 * <p>
 * 硬约束（产品方案 3.2）：
 * <ul>
 *   <li><b>授权有范围</b>：授权的是"该具体目录/文件"，不是整个盘；</li>
 *   <li><b>授权有生命周期</b>：仅本会话有效；</li>
 *   <li><b>授权不继承</b>：子目录不自动成为新的工作空间；</li>
 *   <li><b>授权可追溯</b>：命中时记录日志。</li>
 * </ul>
 * <p>
 * <b>安全边界</b>：只做"文本中出现过该路径"的比对，绝不猜测用户意图。
 * 用户没明确写出的路径一律不授权（走确认流程）。高危路径不受此影响（一直拒绝）。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/9/20
 */
@Slf4j
public class UserPathAuthorizationMatcher {

    /**
     * Windows 绝对路径：盘符 + 冒号 + 分隔符，如 W:\writing、C:/data
     */
    private static final Pattern WINDOWS_ABSOLUTE = Pattern.compile("[A-Za-z]:[\\\\/][^\\s\"'<>|?*，。；：！？“”‘’【】（）]*");

    /**
     * Unix 绝对路径：以 / 开头
     */
    private static final Pattern UNIX_ABSOLUTE = Pattern.compile("/(?:[^\\s\"'<>|?*，。；：！？“”‘’【】（）/]+/)*[^\\s\"'<>|?*，。；：！？“”‘’【】（）/]*");

    /**
     * 从用户消息文本中抽取所有可能的路径，解析为规范化路径集合。
     *
     * @param userText 用户消息文本
     * @return 规范化后的路径集合（可能为空）
     */
    public Set<Path> extractDeclaredPaths(String userText) {
        Set<Path> paths = new LinkedHashSet<>();
        if (StringUtils.isBlank(userText)) {
            return paths;
        }

        // Windows 绝对路径
        Matcher winMatcher = WINDOWS_ABSOLUTE.matcher(userText);
        while (winMatcher.find()) {
            addPath(paths, winMatcher.group());
        }

        // Unix 绝对路径（避免与 Windows 路径重复匹配：Windows 路径里的 / 部分会被单独匹配到）
        if (!userText.contains(":\\") && !userText.contains(":/")) {
            Matcher unixMatcher = UNIX_ABSOLUTE.matcher(userText);
            while (unixMatcher.find()) {
                addPath(paths, unixMatcher.group());
            }
        }

        return paths;
    }

    /**
     * 判断目标路径是否被用户消息明确授权。
     * <p>
     * 判定规则：
     * <ol>
     *   <li>目标路径 ∈ 用户提到的路径 → 授权（用户直接提到了该文件）；</li>
     *   <li>目标路径 ∈ 用户提到的目录之下 → 授权（用户说了"这个目录下"）；</li>
     *   <li>其它 → 不授权。</li>
     * </ol>
     * <p>
     * 注意：<b>不做反向授权</b>（用户提到子文件，不等于授权其父目录）。
     *
     * @param normalizedPath 待访问的规范化路径
     * @param userText       用户消息文本
     * @return 是否命中用户授权
     */
    public boolean isAuthorizedByUserMessage(Path normalizedPath, String userText) {
        if (normalizedPath == null || StringUtils.isBlank(userText)) {
            return false;
        }
        Set<Path> declared = extractDeclaredPaths(userText);
        if (declared.isEmpty()) {
            return false;
        }
        for (Path candidate : declared) {
            try {
                // 1. 命中同一路径
                if (normalizedPath.normalize().equals(candidate.normalize())) {
                    log.info("Path authorized by user message (exact): path={}, declared={}", normalizedPath, candidate);
                    return true;
                }
                // 2. 目标位于用户提到的目录之下
                if (normalizedPath.normalize().startsWith(candidate.normalize())) {
                    log.info("Path authorized by user message (under declared dir): path={}, declared={}",
                            normalizedPath, candidate);
                    return true;
                }
            } catch (Exception e) {
                log.debug("Compare path failed: {} vs {}", normalizedPath, candidate, e);
            }
        }
        return false;
    }

    /**
     * 去重并归一化后加入集合。
     */
    private void addPath(Set<Path> paths, String raw) {
        String cleaned = clean(raw);
        if (StringUtils.isBlank(cleaned)) {
            return;
        }
        try {
            String normalized = cleaned.replace('\\', '/');
            // 去掉结尾的斜杠（保留根路径本身）
            if (normalized.length() > 3 && normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            paths.add(Paths.get(cleaned));
            paths.add(Paths.get(normalized));
        } catch (Exception e) {
            log.debug("Invalid declared path ignored: {}", raw);
        }
    }

    /**
     * 清理路径文本：去除尾部标点、空白。
     */
    private String clean(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String cleaned = raw.trim();
        // 去掉尾部常见中英文标点（这些通常是句子标点，不属于路径）
        while (cleaned.length() > 0) {
            char last = cleaned.charAt(cleaned.length() - 1);
            if (last == '.' || last == ',' || last == ';' || last == ':'
                    || last == '、' || last == '，' || last == '。' || last == '；'
                    || last == '！' || last == '？' || last == ')' || last == '）') {
                cleaned = cleaned.substring(0, cleaned.length() - 1);
            } else {
                break;
            }
        }
        return cleaned;
    }
}

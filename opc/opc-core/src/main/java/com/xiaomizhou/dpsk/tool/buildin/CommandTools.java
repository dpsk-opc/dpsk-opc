package com.xiaomizhou.dpsk.tool.buildin;

import com.xiaomizhou.dpsk.tool.CommandSafetyChecker;
import com.xiaomizhou.dpsk.tool.ToolCategory;
import com.xiaomizhou.dpsk.tool.ToolMeta;
import com.xiaomizhou.dpsk.tool.workspace.FileSystemAccess;
import dev.langchain4j.agent.tool.P;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.xiaomizhou.dpsk.tool.model.ToolMetadata.RISK_DANGEROUS;
import static com.xiaomizhou.dpsk.utils.JsonUtils.toJson;

/**
 * 命令执行工具，提供执行系统命令的能力。
 * <p>
 * 支持设置工作目录、超时时间和环境变量，返回命令的退出码、标准输出和标准错误。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/19
 */
@ToolMeta(value = "执行系统命令", level = RISK_DANGEROUS, category = ToolCategory.BUILD_IN,
        tags = {"命令执行", "系统操作"},
        // 命令类工具无法逐参数声明路径，声明为 WRITE 能力位以便进入边界校验流程：
        // 由 PathExtractor 从命令行中抽取路径，抽取不确定时降级为需要用户确认（不静默放行）。
        filesystemAccess = FileSystemAccess.WRITE)
public class CommandTools {

    /**
     * 默认命令超时时间（秒）
     */
    private static final long DEFAULT_TIMEOUT_SECONDS = 30;

    /**
     * 最大允许的超时时间（秒），防止无限等待
     */
    private static final long MAX_TIMEOUT_SECONDS = 300;

    @dev.langchain4j.agent.tool.Tool(name = "execute_command", value = "执行系统命令并返回结果，包含退出码、标准输出和标准错误。注意：你需要根据当前操作系统自己拼接完整的命令行，例如 Windows 用 cmd /c <命令>，Linux/Mac 用 sh -c \"<命令>\"")
    public String executeCommand(
            @P(description = "完整的命令行字符串。请根据操作系统自行拼接，Windows 用 cmd /c <命令>，Linux/Mac 用 sh -c '<命令>'。例如 Windows: cmd /c dir, Linux: sh -c 'ls -la'") String command,
            @P(description = "工作目录（可选），默认为当前工作目录") String workingDir, @P(description = "超时时间（秒），默认30秒，最大300秒") Integer timeoutSeconds) {

        CommandSafetyChecker.SafetyResult check = CommandSafetyChecker.check(command);

        if (check.isBlocked()) {
            return "危险命令执行被阻止，原因：" + check.getReason();
        }

        Map<String, Object> result = new LinkedHashMap<>();
//        result.put("command", command);

        long timeout = (timeoutSeconds != null && timeoutSeconds > 0)
                ? Math.min(timeoutSeconds, MAX_TIMEOUT_SECONDS)
                : DEFAULT_TIMEOUT_SECONDS;

        try {
            ProcessBuilder processBuilder = buildProcess(command, workingDir);
            Process process = processBuilder.start();

            // 并行读取 stdout 和 stderr
            String stdout = readStream(process.getInputStream());
            String stderr = readStream(process.getErrorStream());

            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                result.put("success", false);
                result.put("message", "命令执行超时（" + timeout + "秒），已强制终止");
                result.put("exitCode", -1);
                result.put("stdout", truncate(stdout, 2000));
                result.put("stderr", truncate(stderr, 2000));
                return toJson(result);
            }

            int exitCode = process.exitValue();
            result.put("success", exitCode == 0);
            result.put("exitCode", exitCode);
            result.put("stdout", truncate(stdout, 2000));
            result.put("stderr", truncate(stderr, 2000));

            if (exitCode != 0 && !stderr.isEmpty()) {
                result.put("message", "命令执行失败，退出码: " + exitCode);
            }

            return toJson(result);

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "命令执行异常: " + e.getMessage());
            result.put("exitCode", -1);
            return toJson(result);
        }
    }

    /**
     * 构建 ProcessBuilder，直接按空格拆分命令。
     * LLM 负责拼接完整的命令行（含 cmd/sh 等 shell 包装）。
     */
    private ProcessBuilder buildProcess(String command, String workingDir) {
        ProcessBuilder pb = new ProcessBuilder(command.split("\\s+"));
        pb.redirectErrorStream(false);

        if (workingDir != null && !workingDir.trim().isEmpty()) {
            java.io.File dir = new java.io.File(workingDir);
            if (dir.isDirectory()) {
                pb.directory(dir);
            }
        }

        return pb;
    }

    /**
     * 读取输入流内容为字符串。
     */
    private String readStream(java.io.InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "读取流失败: " + e.getMessage();
        }
    }

    /**
     * 截断过长的输出，避免返回内容过大。
     */
    private String truncate(String text, int maxLength) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "\n...（输出已截断，共 " + text.length() + " 字符）";
    }
}

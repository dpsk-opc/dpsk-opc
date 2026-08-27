package com.xiaomizhou.dpsk.tool.buildin;

import com.xiaomizhou.dpsk.tool.ToolMeta;
import dev.langchain4j.agent.tool.P;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/6/1 13:56
 * @description
 */
@Slf4j
@ToolMeta(value = "文件工具", level = "normal")
public class FileTools {

    // 可根据需要设置基础工作目录，防止路径穿越，默认不做限制
    private static final Path BASE_DIR = null; // null 表示不限制

    @dev.langchain4j.agent.tool.Tool(name = "list_files", value = "列出目录下的文件和子目录，支持按文件名关键字过滤和递归搜索")
    public String listFiles(
            @ToolParam(description = "目录路径，支持相对或绝对路径")
            @P(description = "目录路径，支持相对或绝对路径")
            String path,
            @ToolParam(description = "搜索关键字（可选），匹配文件名（不区分大小写）")
            @P(description = "搜索关键字（可选），匹配文件名（不区分大小写）")
            String keyword,
            @ToolParam(description = "是否递归搜索子目录，默认 false")
            @P(description = "是否递归搜索子目录，默认 false")
            boolean recursive) {

        try {
            Path dir = resolvePath(path);
            if (!Files.isDirectory(dir)) {
                return "错误：路径不是目录或不存在: " + dir.toAbsolutePath();
            }

            List<String> results = new ArrayList<>();
            if (recursive) {
                Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        addIfMatches(file.getFileName().toString(), keyword, file, results);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes attrs) {
                        if (!d.equals(dir)) {
                            addIfMatches(d.getFileName().toString(), keyword, d, results);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } else {
                try (Stream<Path> stream = Files.list(dir)) {
                    stream.forEach(p -> {
                        String name = p.getFileName().toString();
                        addIfMatches(name, keyword, p, results);
                    });
                }
            }

            if (results.isEmpty()) {
                return keyword == null || keyword.trim().isEmpty() ?
                        "目录为空: " + dir.toAbsolutePath() :
                        "未找到匹配关键字 \"" + keyword + "\" 的文件或目录";
            }

            return "找到 " + results.size() + " 个条目：\n" + String.join("\n", results);

        } catch (IOException e) {
            return "列出文件失败: " + e.getMessage();
        }
    }

    private void addIfMatches(String name, String keyword, Path path, List<String> results) {
        if (keyword == null || keyword.trim().isEmpty() ||
                name.toLowerCase().contains(keyword.toLowerCase())) {
            String type = Files.isDirectory(path) ? "[目录]" : "[文件]";
            results.add(type + " " + path.toAbsolutePath().toString());
        }
    }

    @dev.langchain4j.agent.tool.Tool(name = "read_whole_file", value = "读取整个文件内容（文本）")
    public String readWholeFile(
            @ToolParam(description = "文件路径")
            @P(description = "文件路径") String path) {

        try {
            Path file = resolvePath(path);
            if (!Files.isRegularFile(file)) {
                return "错误：路径不是文件或不存在: " + file.toAbsolutePath();
            }
            byte[] bytes = Files.readAllBytes(file);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "读取文件失败: " + e.getMessage();
        }
    }

    @Tool(name = "read_file_lines", description = "读取文件的指定行范围（从 startLine 到 endLine，包含两端）。行号从 1 开始。若只指定 startLine，则读取单行。")
    @dev.langchain4j.agent.tool.Tool(name = "read_file_lines", value = "读取文件的指定行范围（从 startLine 到 endLine，包含两端）。行号从 1 开始。若只指定 startLine，则读取单行。")
    public String readFileLines(
            @ToolParam(description = "文件路径") String path,
            @ToolParam(description = "起始行号（从1开始），必填") int startLine,
            @ToolParam(description = "结束行号（可选，不填则只读起始行") Integer endLine) {

        try {
            Path file = resolvePath(path);
            if (!Files.isRegularFile(file)) {
                return "错误：路径不是文件或不存在: " + file.toAbsolutePath();
            }

            List<String> allLines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (startLine < 1 || startLine > allLines.size()) {
                return "错误：起始行号超出范围（文件共 " + allLines.size() + " 行）";
            }

            int end = (endLine == null) ? startLine : endLine;
            if (end < startLine || end > allLines.size()) {
                return "错误：结束行号超出范围（文件共 " + allLines.size() + " 行）";
            }

            // 转换为0基索引
            List<String> selected = allLines.subList(startLine - 1, end);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < selected.size(); i++) {
                sb.append(startLine + i).append(": ").append(selected.get(i)).append("\n");
            }
            return sb.toString();

        } catch (IOException e) {
            return "读取文件行失败: " + e.getMessage();
        }
    }

    @Tool(name = "append_to_file", description = "向文件末尾追加内容。若文件不存在则自动创建。")
    @dev.langchain4j.agent.tool.Tool(name = "append_to_file", value = "向文件末尾追加内容。若文件不存在则自动创建。")
    public String appendToFile(
            @ToolParam(description = "文件路径") String path,
            @ToolParam(description = "要追加的内容") String content) {

        try {
            Path file = resolvePath(path);
            // 确保父目录存在
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, content,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return "成功追加内容到文件: " + file.toAbsolutePath();
        } catch (IOException e) {
            log.error("appendToFile error", e);
            return "追加文件失败: " + e.getMessage();
        }
    }

    @Tool(name = "insert_into_file", description = "在文件的指定行号前插入内容（行号从1开始）。插入后原行及之后的内容后移。")
    @dev.langchain4j.agent.tool.Tool(name = "insert_into_file", value = "在文件的指定行号前插入内容（行号从1开始）。插入后原行及之后的内容后移。")
    public String insertIntoFile(
            @ToolParam(description = "文件路径") String path,
            @ToolParam(description = "要插入的内容（可包含换行）") String content,
            @ToolParam(description = "插入行号（从1开始，在该行之前插入）") int lineNumber) {

        try {
            Path file = resolvePath(path);
            if (!Files.isRegularFile(file)) {
                return "错误：文件不存在，无法执行插入: " + file.toAbsolutePath();
            }

            List<String> allLines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (lineNumber < 1 || lineNumber > allLines.size() + 1) {
                return "错误：行号超出范围（文件共 " + allLines.size() + " 行，有效插入范围 1 ~ " + (allLines.size() + 1) + "）";
            }

            // 拆分内容为多行
            String[] linesToInsert = content.split("\\R", -1);
            // 插入点前部分
            List<String> newLines = new ArrayList<>(allLines.subList(0, lineNumber - 1));
            // 插入新内容
            newLines.addAll(Arrays.asList(linesToInsert));
            // 插入点后部分
            newLines.addAll(allLines.subList(lineNumber - 1, allLines.size()));

            // 写回文件
            Files.write(file, newLines, StandardCharsets.UTF_8);
            return String.format("成功在文件 %s 的第 %d 行前插入内容，原文件共 %d 行，现在共 %d 行。",
                    file.toAbsolutePath(), lineNumber, allLines.size(), newLines.size());

        } catch (IOException e) {
            log.error("insertIntoFile error", e);
            return "插入文件失败: " + e.getMessage();
        }
    }

    @Tool(name = "search_in_file", description = "在文件中搜索包含指定关键字的行，返回行号和内容")
    @dev.langchain4j.agent.tool.Tool(name = "search_in_file", value = "在文件中搜索包含指定关键字的行，返回行号和内容")
    public String searchInFile(
            @ToolParam(description = "文件路径") String path,
            @ToolParam(description = "搜索关键字（区分大小写）") String keyword) {

        try {
            Path file = resolvePath(path);
            if (!Files.isRegularFile(file)) {
                return "错误：路径不是文件或不存在: " + file.toAbsolutePath();
            }

            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            List<String> matches = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).contains(keyword)) {
                    matches.add((i + 1) + ": " + lines.get(i));
                }
            }

            if (matches.isEmpty()) {
                return "未在文件中找到关键字 \"" + keyword + "\"";
            }
            return "找到 " + matches.size() + " 处匹配：\n" + String.join("\n", matches);

        } catch (IOException e) {
            return "搜索文件内容失败: " + e.getMessage();
        }
    }

    // 工具方法：路径解析与安全检查
    private Path resolvePath(String userPath) throws IOException {
        Path p = Paths.get(userPath).normalize();
        return p;
    }

}

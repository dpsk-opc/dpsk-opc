package com.xiaomizhou.dpsk.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.google.common.collect.Maps;
import com.xiaomizhou.dpsk.constant.KnowledgeLibStatus;
import com.xiaomizhou.dpsk.db.FileService;
import com.xiaomizhou.dpsk.db.dao.KnowledgeLibDao;
import com.xiaomizhou.dpsk.db.dao.KnowledgeNodeDao;
import com.xiaomizhou.dpsk.db.model.FileRecord;
import com.xiaomizhou.dpsk.db.model.KnowledgeLib;
import com.xiaomizhou.dpsk.db.model.KnowledgeNode;
import com.xiaomizhou.dpsk.memory.manager.FactManager;
import com.xiaomizhou.dpsk.memory.store.EmbeddingStore;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 知识库构建服务。
 * <p>
 * 负责将知识库中的文件解析、分块、向量化，存入 L3 向量存储。
 * 被 {@link com.xiaomizhou.dpsk.task.consumer.KnowLedgeBuildTaskConsumer} 调用。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/15
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KnowledgeBuildService {

    private final KnowledgeNodeDao knowledgeNodeDao;
    private final KnowledgeLibDao knowledgeLibDao;
    private final FileService fileService;

    /** 可处理的文本文件后缀（代码和文档格式） */
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            ".txt", ".md", ".markdown",
            ".java", ".py", ".js", ".ts", ".jsx", ".tsx",
            ".c", ".cpp", ".h", ".hpp", ".cs", ".go", ".rs", ".rb",
            ".xml", ".json", ".yaml", ".yml", ".toml",
            ".properties", ".ini", ".cfg", ".conf",
            ".sql", ".sh", ".bat", ".ps1",
            ".html", ".htm", ".css", ".scss", ".less",
            ".vue", ".svelte",
            ".gradle", ".kt", ".kts",
            ".proto", ".thrift"
    );

    /** 分块大小（字符数） */
    private static final int CHUNK_SIZE = 1000;

    /** 分块重叠（字符数） */
    private static final int CHUNK_OVERLAP = 200;

    /** 单文件最大处理大小（10MB） */
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    /**
     * 构建知识库。
     * <p>
     * 流程：查询 UPLOADED 状态的文件节点 → 过滤可处理文件 → 逐文件解析/分块/向量化 → 更新状态。
     *
     * @param embeddingClient 向量化客户端
     * @param l3EmbeddingStore L3 向量存储
     * @return 构建结果摘要
     */
    public BuildResult build(FactManager.EmbeddingClient embeddingClient,
                             EmbeddingStore l3EmbeddingStore) {

        try {
            // 1. 查询 UPLOADED 状态的文件节点
            List<KnowledgeNode> fileNodes = knowledgeNodeDao.listFileNodesByStatus(KnowledgeLibStatus.UPLOADED, 10);
            if (fileNodes.isEmpty()) {
                return BuildResult.ok(0, 0, 0);
            }

//            Set<String> unsupportNodes = Sets.newHashSet();

            // 2. 过滤：只处理支持的文件类型 + 文件存在 + 大小合法
            List<FileNodeInfo> pendingFiles = fileNodes.stream()
                    .map(this::resolveFileInfo)
                    .filter(Objects::nonNull).toList();
//                    .filter(node -> {
//
//                        if (isSupportedFile(node)) {
//                            return true;
//                        }
//
//                        unsupportNodes.add(node.node.getCode());
//                        return false;
//                    }).toList();

//            // 不支持类型标记为 UNSUPPORT
//            if (CollectionUtils.isNotEmpty(unsupportNodes)) {
//                unsupportNodes.forEach(nodeCode -> {
//                    knowledgeNodeDao.casUpdateStatus(nodeCode, KnowledgeLibStatus.UPLOADED, KnowledgeLibStatus.UNSUPPORT);
//                });
//            }

            if (pendingFiles.isEmpty()) {
                // 所有文件都不支持或无法处理，标记知识库为 LEARNED
                return BuildResult.ok(0, 0, fileNodes.size());
            }

            Map<String, KnowledgeLib> libs = Maps.newHashMap();

            List<String> libCodes = fileNodes.stream().map(KnowledgeNode::getLibCode).distinct().toList();
            if (CollectionUtils.isNotEmpty(libCodes)) {
                libs.putAll(knowledgeLibDao.list(Wrappers.<KnowledgeLib>lambdaQuery().in(KnowledgeLib::getCode, libCodes)).stream().collect(Collectors.toMap(KnowledgeLib::getCode, Function.identity())));
            }


            int totalChunks = 0;
            int successFiles = 0;
            int failedFiles = 0;
            List<String> failedFileNames = new ArrayList<>();

            for (FileNodeInfo info : pendingFiles) {
                // CAS 将节点状态从 UPLOADED 改为 ANALYZING
                if (!knowledgeNodeDao.casUpdateStatus(
                        info.node.getCode(), KnowledgeLibStatus.UPLOADED, KnowledgeLibStatus.ANALYZING)) {
                    log.info("节点 {} 状态已被变更，跳过", info.node.getCode());
                    continue;
                }


                try {
                    int chunks = processFile(info, libs.get(info.node.getLibCode()), embeddingClient, l3EmbeddingStore);
                    totalChunks += chunks;
                    successFiles++;
                    // 标记节点为 LEARNED
                    knowledgeNodeDao.casUpdateStatus(
                            info.node.getCode(), KnowledgeLibStatus.ANALYZING, KnowledgeLibStatus.LEARNED);
                    log.info("文件处理完成: node={}, file={}, chunks={}",
                            info.node.getCode(), info.fileRecord.getOriginalName(), chunks);
                } catch (Exception e) {
                    failedFiles++;
                    failedFileNames.add(info.fileRecord.getOriginalName());
                    // 标记节点为 FAILED
                    knowledgeNodeDao.casUpdateStatus(
                            info.node.getCode(), KnowledgeLibStatus.ANALYZING, KnowledgeLibStatus.FAILED);
                    log.error("文件处理失败: node={}, file={}",
                            info.node.getCode(), info.fileRecord.getOriginalName(), e);
                }
            }

            return BuildResult.ok(successFiles, failedFiles, totalChunks);

        } catch (Exception e) {
            log.error("知识库构建异常", e);
            return BuildResult.fail("构建异常: " + e.getMessage());
        }
    }

    /**
     * 处理单个文件：读取 → 分块 → 向量化 → 存入向量库
     */
    private int processFile(FileNodeInfo info,
                            KnowledgeLib lib,
                            FactManager.EmbeddingClient embeddingClient,
                            EmbeddingStore l3EmbeddingStore) throws IOException {

        String path = fileService.getDiskFilePath(info.fileRecord.getCode());
        if (StringUtils.isBlank(path)) {
            throw new IOException("磁盘文件不存在: " + info.fileRecord.getCode());
        }

        Document document;
        try {
            document = FileSystemDocumentLoader.loadDocument(path, new ApacheTikaDocumentParser());
        } catch (Exception e) {
            log.warn("知识库文件加载失败!", e);
            return 0;
        }

        String content = document.text();
        if (StringUtils.isBlank(content)) {
            log.info("文件内容为空: {}", info.fileRecord.getOriginalName());
            return 0;
        }

        // 分块
        List<String> chunks = splitChunks(content);
        log.debug("文件 {} 分为 {} 块", info.fileRecord.getOriginalName(), chunks.size());

        // 逐块向量化并存入 L3 向量库
        int stored = 0;
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            try {
                List<Float> vector = embeddingClient.embed(chunk);
                if (vector == null || vector.isEmpty()) {
                    log.warn("向量化为空: file={}, chunk={}", info.fileRecord.getOriginalName(), i);
                    continue;
                }

                Map<String, String> metadata = new LinkedHashMap<>();
                metadata.put("type", "knowledge");
                metadata.put("lib_code", lib.getCode());
                metadata.put("lib_name", lib.getName());
                metadata.put("owner_code", lib.getOwnerCode());
                metadata.put("node_code", info.node.getCode());
                metadata.put("file_name", info.fileRecord.getOriginalName());
                metadata.put("chunk_index", String.valueOf(i));
                metadata.put("total_chunks", String.valueOf(chunks.size()));
                metadata.put("timestamp", new Date().toInstant().toString());

                l3EmbeddingStore.add(vector, chunk, metadata);
                stored++;
            } catch (Exception e) {
                log.error("向量化/存储失败: file={}, chunk={}", info.fileRecord.getOriginalName(), i, e);
            }
        }
        return stored;
    }

    /**
     * 文本分块（滑动窗口）。
     */
    List<String> splitChunks(String content) {
        if (content == null || content.isEmpty()) {
            return Collections.emptyList();
        }

        content = content.replace("\n", "").replace("\r", "").replace("\t", "");
        if (content.length() <= CHUNK_SIZE) {
            return Collections.singletonList(content);
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < content.length()) {
            int end = Math.min(start + CHUNK_SIZE, content.length());
            chunks.add(content.substring(start, end));
            start += (CHUNK_SIZE - CHUNK_OVERLAP);
        }
        return chunks;
    }

    /**
     * 解析文件节点关联的文件信息。
     */
    private FileNodeInfo resolveFileInfo(KnowledgeNode node) {
        if (StringUtils.isBlank(node.getFileCode())) {
            return null;
        }
        FileRecord fileRecord = fileService.getByCode(node.getFileCode());
        if (fileRecord == null) {
            log.warn("节点 {} 关联的文件 {} 不存在", node.getCode(), node.getFileCode());
            return null;
        }
        return new FileNodeInfo(node, fileRecord);
    }

    /**
     * 判断文件是否可处理。
     */
    private boolean isSupportedFile(FileNodeInfo info) {
        // 检查文件扩展名
        String ext = info.fileRecord.getFileExtension();
        if (ext == null || !SUPPORTED_EXTENSIONS.contains(ext.toLowerCase())) {
            log.info("不支持的文件类型: {} (ext={})", info.fileRecord.getOriginalName(), ext);
            return false;
        }
        // 检查文件大小
        if (info.fileRecord.getFileSize() > MAX_FILE_SIZE) {
            log.info("文件过大跳过: {} (size={})", info.fileRecord.getOriginalName(), info.fileRecord.getFileSize());
            return false;
        }
        return true;
    }

    // ======================== 内部类 ========================

    private static class FileNodeInfo {
        final KnowledgeNode node;
        final FileRecord fileRecord;

        FileNodeInfo(KnowledgeNode node, FileRecord fileRecord) {
            this.node = node;
            this.fileRecord = fileRecord;
        }
    }

    public static class BuildResult {
        private final boolean success;
        private final boolean skipped;
        private final int successFiles;
        private final int failedFiles;
        private final int totalChunks;
        private final String message;

        private BuildResult(boolean success, boolean skipped, int successFiles, int failedFiles,
                            int totalChunks, String message) {
            this.success = success;
            this.skipped = skipped;
            this.successFiles = successFiles;
            this.failedFiles = failedFiles;
            this.totalChunks = totalChunks;
            this.message = message;
        }

        public static BuildResult ok(int successFiles, int failedFiles, int totalChunks) {
            return new BuildResult(true, false, successFiles, failedFiles, totalChunks, "OK");
        }

        public static BuildResult skip(String message) {
            return new BuildResult(true, true, 0, 0, 0, message);
        }

        public static BuildResult fail(String message) {
            return new BuildResult(false, false, 0, 0, 0, message);
        }

        public boolean isSuccess() { return success; }
        public boolean isSkipped() { return skipped; }
        public int getSuccessFiles() { return successFiles; }
        public int getFailedFiles() { return failedFiles; }
        public int getTotalChunks() { return totalChunks; }
        public String getMessage() { return message; }
    }
}

package com.xiaomizhou.dpsk.rag;

import com.xiaomizhou.dpsk.rag.model.Document;
import com.xiaomizhou.dpsk.rag.model.RagFilter;
import com.xiaomizhou.dpsk.rag.model.RagHit;

import java.util.List;
import java.util.Map;

/**
 * RAG 服务接口 —— 统一的向量检索与索引管理能力。
 * <p>
 * 抽象底层向量数据库（JVector / Milvus / 云服务），通过 {@link RagNamespace} 实现不同领域的数据物理隔离。
 * <p>
 * <b>设计原则：</b>
 * <ul>
 *   <li>所有操作需指定 namespace，确保 skill/tool/knowledge 等数据独立存储</li>
 *   <li>检索支持 {@link RagFilter} 做权限/状态/归属等条件过滤</li>
 *   <li>接口方法尽量保持简洁，复杂能力通过 RagFilter 组合实现</li>
 * </ul>
 *
 * <h3>典型用法</h3>
 * <pre>{@code
 * // 写入工具描述到 RAG
 * ragService.addDocument(RagNamespace.TOOL,
 *     Document.of("tool_001", "搜索文件的工具", Map.of("status", "enabled", "owner", "agent_a")));
 *
 * // 按权限检索工具
 * RagFilter filter = RagFilter.builder()
 *     .equals("status", "enabled")
 *     .in("visible_to", Set.of("user_001"))
 *     .build();
 * List<RagHit> hits = ragService.search(RagNamespace.TOOL, "文件搜索", 10, 0.7, filter);
 * }</pre>
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/21
 */
public interface RagService {

    // ==================== 数据写入 ====================

    /**
     * 添加文档到指定 namespace（自动生成 ID）。
     *
     * @param namespace 命名空间
     * @param document  文档
     * @return 生成的文档 ID
     */
    String addDocument(RagNamespace namespace, Document document);

    /**
     * 批量添加文档。
     *
     * @param namespace 命名空间
     * @param documents 文档列表
     * @return 生成的文档 ID 列表（与输入顺序对应）
     */
    List<String> addDocuments(RagNamespace namespace, List<Document> documents);

    // ==================== 数据检索 ====================

    /**
     * 语义检索（基础版，无过滤条件）。
     *
     * @param namespace 命名空间
     * @param query     查询文本
     * @param topK      最大返回数
     * @param minScore  最低相似度阈值 (0.0 ~ 1.0)
     * @return 命中结果列表（按分数降序）
     */
    List<RagHit> search(RagNamespace namespace, String query, int topK, double minScore);

    /**
     * 带过滤条件的语义检索。
     * <p>
     * 支持 {@link RagFilter} 中定义的所有过滤类型：
     * equals / in / notEquals / gte / lte / contains，AND 关系。
     *
     * @param namespace 命名空间
     * @param query     查询文本
     * @param topK      最大返回数
     * @param minScore  最低相似度阈值
     * @param filter    过滤条件（null 或 empty 表示不过滤）
     * @return 命中结果列表（按分数降序）
     */
    List<RagHit> search(RagNamespace namespace, String query, int topK, double minScore, RagFilter filter);

    // ==================== 数据管理 ====================

    /**
     * 删除指定 ID 的文档。
     *
     * @param namespace 命名空间
     * @param id        文档 ID
     */
    void deleteById(RagNamespace namespace, String id);

    /**
     * 按 metadata 条件批量删除（仅支持 equals 精确匹配）。
     *
     * @param namespace 命名空间
     * @param filter    过滤条件（key=value 精确匹配，AND 关系）
     * @return 删除的文档数量
     */
    int deleteByFilter(RagNamespace namespace, Map<String, String> filter);

    /**
     * 更新文档（先删后加）。
     *
     * @param namespace 命名空间
     * @param id        文档 ID
     * @param document  新文档内容
     * @return 文档 ID
     */
    default String updateDocument(RagNamespace namespace, String id, Document document) {
        deleteById(namespace, id);
        return addDocument(namespace, Document.of(id, document.getText(), document.getMetadata()));
    }

    /**
     * 统计指定 namespace 的文档数量。
     */
    long count(RagNamespace namespace);

    /**
     * 按条件统计文档数量。
     */
    long count(RagNamespace namespace, Map<String, String> filter);

    // ==================== 生命周期 ====================

    /**
     * 清空指定 namespace 的所有数据。
     */
    void clearNamespace(RagNamespace namespace);

    /**
     * 服务是否可用（健康检查）。
     */
    boolean isAvailable();

    /**
     * 关闭资源，释放底层存储。
     */
    void shutdown();
}

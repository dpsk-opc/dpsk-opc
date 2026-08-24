# RAG 召回率优化与评估方案

## 一、总体架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                         RAG 全链路                                   │
├───────────────┬──────────────┬──────────────┬───────────────────────┤
│   索引阶段     │   Query 改写  │   多路检索    │   后处理               │
│               │              │              │                       │
│  文档解析      │  规则改写     │  向量检索     │   Rerank 精排          │
│    ↓          │  (同义词扩展) │  (JVector)   │    ↓                  │
│  文本清洗      │   +          │  +           │   段落上下文扩展        │
│    ↓          │  LLM 改写     │  关键词检索   │    ↓                  │
│  滑动窗口分块   │  (多query)   │  (BM25)      │   注入 LLM Prompt      │
│    ↓          │              │              │                       │
│  噪声过滤      │              │              │                       │
│    ↓          │              │              │                       │
│  双粒度索引     │              │              │                       │
│  (句子级+段落级)│              │              │                       │
└───────────────┴──────────────┴──────────────┴───────────────────────┘
```

---

## 二、索引阶段优化

### 2.1 滑动窗口分块策略

将当前的"逐句分块"升级为"滑动窗口段落分块"。

**流程**：

```
原始文本
  → TextCleaner.clean()                    // 清洗 HTML/Markdown/控制字符
  → 按空行切分为段落 blocks[]               // 段落作为强边界
  → 每段用 Stanford CoreNLP 分句 sentences[]
  → 滑动窗口合并:
      窗口大小: 3-5 句 (约 200-500 字)
      步长: 1-2 句 (保证相邻窗口重叠 2-3 句)
  → 噪声过滤:
      - 去除纯标点/纯数字/纯空白句
      - 去除长度 < minChunkLength (默认 10 字)
      - 去除仅包含 URL/邮箱/日期的句子
      - 去除页眉页脚重复模式
```

**参数配置**：

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `windowSize` | 3 | 每窗口句子数 |
| `stepSize` | 1 | 滑动步长（句子数） |
| `minChunkLength` | 10 | 最小 chunk 长度（字符），短于此丢弃 |
| `maxChunkLength` | 500 | 最大 chunk 长度（字符），超出按句子边界截断 |
| `overlapRatio` | 0.67 | 重叠比例（windowSize=3, stepSize=1 → 67% 重叠） |

**示例**：

```
原文段落：S1. S2. S3. S4. S5. S6.

windowSize=3, stepSize=1:
  Chunk 0: [S1, S2, S3]
  Chunk 1: [S2, S3, S4]    ← 与 Chunk 0 重叠 S2, S3
  Chunk 2: [S3, S4, S5]    ← 与 Chunk 1 重叠 S3, S4
  Chunk 3: [S4, S5, S6]

优势：任意连续 2 句的语义都能被至少一个 chunk 完整覆盖。
```

### 2.2 双粒度索引

每个文档同时写入两种粒度的 chunk：

| 粒度 | 存储内容 | metadata 标记 | 用途 |
|------|----------|--------------|------|
| **Sentence-level** | 滑动窗口 chunk | `grain=sentence` | 语义检索匹配（高召回） |
| **Paragraph-level** | 完整段落文本 | `grain=paragraph` | 上下文扩展（解决语义零碎） |

**metadata 增强**：

```java
// Sentence-level chunk 的 metadata
metadata.put("type", "knowledge");
metadata.put("grain", "sentence");           // 新增：粒度标记
metadata.put("lib_code", lib.getCode());
metadata.put("node_code", info.node.getCode());
metadata.put("file_name", info.fileRecord.getOriginalName());
metadata.put("chunk_index", String.valueOf(i));       // chunk 序号
metadata.put("paragraph_index", String.valueOf(p));   // 新增：所属段落编号
metadata.put("sentence_start", String.valueOf(s));    // 新增：起始句在段落中的索引
metadata.put("sentence_end", String.valueOf(e));      // 新增：结束句在段落中的索引
metadata.put("total_paragraphs", String.valueOf(t));  // 新增：文档总段落数

// Paragraph-level chunk 的 metadata
metadata.put("type", "knowledge");
metadata.put("grain", "paragraph");          // 段落级标记
metadata.put("paragraph_index", String.valueOf(p));
// ... 其余同上
```

### 2.3 噪声过滤规则

在 `NlpUtils` 或新增 `ChunkFilter` 中实现：

```java
public class ChunkFilter {
    // 需过滤的模式
    private static final Pattern PURE_PUNCTUATION = Pattern.compile("^[\\p{P}\\s]+$");
    private static final Pattern PURE_DIGITS = Pattern.compile("^[\\d.,%\\s]+$");
    private static final Pattern ONLY_URL = Pattern.compile("^https?://\\S+$");
    private static final Pattern PAGE_NUMBER = Pattern.compile("^\\s*\\d{1,4}\\s*$");
    private static final Pattern TOC_LINE = Pattern.compile("^\\s*\\d+(\\.\\d+)*\\s+.{0,30}$");

    public static boolean shouldKeep(String text, int minLength) {
        if (text == null || text.isBlank()) return false;
        if (text.length() < minLength) return false;
        if (PURE_PUNCTUATION.matcher(text).matches()) return false;
        if (PURE_DIGITS.matcher(text).matches()) return false;
        if (ONLY_URL.matcher(text).matches()) return false;
        return true;
    }
}
```

---

## 三、检索阶段优化

### 3.1 Query 改写

#### 3.1.1 规则改写（轻量级，默认启用）

```java
public class QueryRewriter {
    /**
     * 规则改写：不做 LLM 调用，纯文本处理。
     */
    public List<String> rewrite(String query) {
        List<String> variants = new ArrayList<>();
        variants.add(query);  // 原始 query

        // 1. 去除礼貌用语
        String cleaned = query
            .replaceAll("请问|你好|您好|麻烦|帮忙|能不能|可不可以", "")
            .replaceAll("[？?！!，,。.]$", "")
            .trim();
        if (!cleaned.equals(query)) variants.add(cleaned);

        // 2. 提取关键短语（取最长的子句）
        String[] clauses = query.split("[，,；;。！!？?]");
        for (String clause : clauses) {
            String trimmed = clause.trim();
            if (trimmed.length() > 5 && !variants.contains(trimmed)) {
                variants.add(trimmed);
            }
        }

        return variants;
    }
}
```

#### 3.1.2 LLM 改写（可选，对复杂 query 启用）

```text
Prompt: "将以下问题改写为 2-3 个不同角度的检索查询，每个查询应简洁、关键词丰富：

原始问题：{query}

改写结果（每行一个）："
```

**启用条件**：query 长度 > 20 字 且 首次检索 top1 score < 0.85 时触发。

### 3.2 多路检索

| 路径 | 方式 | 权重 | 说明 |
|------|------|------|------|
| 向量检索 | JVector ANN (cosine) | 1.0 | 语义匹配，主路径 |
| 关键词检索 | 内存倒排索引 (TF-IDF) | 0.5 | 精确关键词匹配 |
| 实体检索 | metadata filter (exact match) | 0.3 | 人名/地名/术语精确过滤 |

**融合策略**：RRF (Reciprocal Rank Fusion)

```java
// RRF 分数计算
for each document d:
    rrfScore(d) = Σ ( weight_i / (k + rank_i(d)) )
    // k = 60 (RRF 标准常数)
    // weight_i = 各路权重
```

### 3.3 Rerank 精排

#### 3.3.1 方案 A：Cross-Encoder 模型（推荐）

使用 **bge-reranker-v2-m3**（BAAI 开源，ONNX 格式），在本地 CPU 推理。

**模型信息**：

| 属性 | 值 |
|------|-----|
| 模型名称 | BAAI/bge-reranker-v2-m3 |
| 模型大小 | ~568 MB |
| 最大输入长度 | 8192 tokens |
| 输入格式 | `[CLS] query [SEP] document [SEP]` |
| 输出 | 单一相关性分数 (0~1) |

**推理方式**：ONNX Runtime，支持 batch 推理（一次推理多条候选）。

#### 3.3.2 Rerank 耗时评估

**测试环境假设**：
- CPU：8 核，2.5GHz（典型服务器/开发机配置）
- 无 GPU 加速
- ONNX Runtime 线程数：4

**耗时拆解**：

| 阶段 | 子步骤 | 单次耗时 | 说明 |
|------|--------|----------|------|
| Tokenize | query + 1 doc | ~1ms | WordPiece tokenizer |
| 推理 | 1 pair (query + doc) | **50~150ms** | 取决于 token 数 |
| 推理 | 9 pairs batch | **120~350ms** | batch 推理比逐条快 3-5x |
| 推理 | 15 pairs batch | **180~500ms** | |
| 推理 | 30 pairs batch | **350~900ms** | |

**关键变量**：

| 变量 | 影响 |
|------|------|
| token 数（query + doc 长度） | 每增加 100 tokens，推理耗时约增加 15-25% |
| batch size | 从 1→9，总耗时仅增加约 2-3x（不是 9x） |
| CPU 核心数 | ONNX 可利用多线程，4→8 核提升约 60-80% |

**推荐配置下的耗时估算**：

```
场景：topK=3 最终返回，粗排取 9 候选
  → batch_size=9
  → 单次 rerank 耗时：150~350ms（中位数 ~200ms）
```

#### 3.3.3 方案 B：LLM Rerank（备选）

用 LLM 对候选打分，适合小批量场景（topK ≤ 5）。

```text
Prompt: "评估以下文档片段与问题的相关性，输出 0-10 的分数。

问题：{query}

文档片段 1：{doc1}
文档片段 2：{doc2}
...

请以 JSON 格式输出：[{"id":1, "score": 8.5}, ...]"
```

**耗时**：取决于 LLM 响应速度，通常 500-2000ms（远程 API）。

#### 3.3.4 方案 C：规则 Rerank（兜底）

不依赖模型，纯规则打分：

```java
finalScore = vectorScore * 0.6
           + keywordOverlapRatio(query, doc) * 0.25
           + entityMatchRatio(query, doc) * 0.15
```

**耗时**：< 1ms（无推理开销）。

#### 3.3.5 Rerank 方案对比

| 方案 | 精排耗时 | Precision 提升 | 适用场景 |
|------|----------|---------------|----------|
| 规则 Rerank | < 1ms | +3~5% | 对延迟敏感，兜底 |
| Cross-Encoder (ONNX) | 150~350ms | +8~15% | **推荐**，性价比最优 |
| LLM Rerank | 500~2000ms | +5~10% | 候选少，需要深度理解 |

### 3.4 段落上下文扩展

**问题**：句子级 chunk 语义零碎，LLM 难以理解上下文。

**方案**：检索命中 sentence-level chunk → 通过 `paragraph_index` 查出段落级 chunk → 扩展前后各一段。

```
检索结果：
  Hit: chunk_sentence_7, paragraph_index=3, score=0.92

上下文扩展：
  → 查询 grain=paragraph, paragraph_index in [2, 3, 4]
  → 拼接为完整上下文：
      [段落 2] ...前一段落内容...
      [段落 3] ...命中段落内容...  ← 包含命中句子
      [段落 4] ...后一段落内容...
  → 注入 LLM prompt
```

**实现要点**：
- 利用双粒度索引，paragraph 查询用 metadata filter 精确匹配（不依赖语义检索）
- 扩展后对段落级结果去重（多个 sentence hit 可能命中同一段落）
- 上下文总长度控制在 2000 字以内，超出则截断

---

## 四、全链路耗时评估

### 4.1 耗时模型

```
T_total = T_query_rewrite + T_embedding + T_ann_search + T_rerank + T_context_expand
```

### 4.2 各环节耗时估算

**假设条件**：
- CPU：8 核，2.5GHz
- JVector 索引规模：1 万条 chunks
- Embedding 模型：AllMiniLmL6V2 (ONNX, 384维)
- Rerank 模型：bge-reranker-v2-m3 (ONNX, batch_size=9)
- Query 长度：30 字（中文）
- Chunk 长度：200 字（中文）

| 环节 | 子步骤 | 估计耗时 | 备注 |
|------|--------|----------|------|
| **T1 Query 改写** | | **5~15ms** | |
| | 规则改写 | 1~2ms | 纯正则，无 IO |
| | LLM 改写（可选） | 500~2000ms | 仅复杂 query 触发 |
| **T2 Embedding** | | **10~20ms** | |
| | Query 向量化 | 5~15ms | AllMiniLmL6V2, 384维 |
| | 多 query 向量化 | 10~20ms × N | 每增加一个改写 query |
| **T3 ANN 检索** | | **5~20ms** | |
| | JVector 检索 (1万条) | 3~8ms | DiskANN, 内存索引 |
| | metadata 后过滤 | 1~5ms | 纯内存 Map 匹配 |
| | RRF 融合 | 1~3ms | 多路结果合并排序 |
| **T4 Rerank** | | **150~350ms** | **占比最大** |
| | Tokenize (batch) | 2~5ms | 9 条候选 |
| | Cross-Encoder 推理 | 120~300ms | ONNX batch 推理 |
| | 分数排序 | < 1ms | |
| **T5 上下文扩展** | | **5~15ms** | |
| | 按 paragraph_index 查询段落 | 3~8ms | metadata filter 精确查 |
| | 段落拼接 + 去重 | 1~3ms | 纯字符串操作 |
| **总计 (不含 LLM 改写)** | | **~180~420ms** | **中位数 ~250ms** |
| **总计 (含 LLM 改写)** | | **~700~2500ms** | 取决于 LLM API 响应速度 |

### 4.3 耗时分布（饼图）

```
不含 LLM 改写:
  Query 改写    5ms   ( 2%)  ▏
  Embedding    15ms   ( 6%)  ▎
  ANN 检索     10ms   ( 4%)  ▎
  Rerank      200ms   (80%)  ████████████████████████████████
  上下文扩展    10ms   ( 4%)  ▎
  其他开销     10ms   ( 4%)  ▎
  ────────────────────────────
  总计        250ms

含 LLM 改写 (远程 API):
  LLM 改写   1200ms   (77%)  ███████████████████████████████████████████████████
  Embedding    15ms   ( 1%)  ▏
  ANN 检索     10ms   ( 1%)  ▏
  Rerank      200ms   (13%)  ████████
  上下文扩展    10ms   ( 1%)  ▏
  其他开销     15ms   ( 1%)  ▏
  ────────────────────────────
  总计       1450ms
```

### 4.4 关键结论

1. **Rerank 是最大耗时瓶颈**：占全链路 80%，但这是必要的代价——它也是 Precision 提升最大的环节（+8~15%）。

2. **Rerank batch 推理是关键优化点**：逐条推理 9 条需要 450~1350ms，batch 推理仅需 120~300ms，提升 3-5x。

3. **全链路 250ms 在可接受范围内**：作为知识库检索的耗时，对用户体验影响有限（用户感知阈值约 500ms）。

4. **LLM 改写是可选开销**：仅在首次检索分数低时触发，正常场景不增加耗时。

### 4.5 优化方向

| 优化手段 | 预期效果 | 代价 |
|----------|----------|------|
| Rerank 候选数从 9→5 | 耗时 -40%，Precision -2~3% | 低 |
| Embedding 模型升级（如 bge-small-zh, 512维） | Embedding 质量提升，Rerank 候选更准 | 模型变大 |
| Rerank 模型量化（INT8） | 推理耗时 -30~50%，精度损失 < 1% | 需转换模型 |
| 预计算 chunk embedding 缓存 | ANN 检索从 10ms → 2ms（缓存命中时） | 内存 |
| 异步 Rerank（先返回 top1，后台精排） | 用户感知延迟 → 50ms | 架构复杂度 |

---

## 五、科学评估体系

### 5.1 数据集构建

#### 5.1.1 目录结构

```
opc-im/src/test/resources/rag-eval/
├── config.json                    # 评估配置
├── documents/                     # 测试文档（多格式）
│   ├── tech-report.pdf           # PDF 文档
│   ├── api-doc.html              # HTML 文档
│   ├── meeting-notes.txt         # 纯文本文档
│   ├── product-manual.docx       # Word 文档
│   └── data-table.xlsx           # Excel 文档
├── qa/                           # 标注 QA 对
│   ├── qa-tech-report.json
│   ├── qa-api-doc.json
│   ├── qa-meeting-notes.json
│   ├── qa-product-manual.json
│   └── qa-data-table.json
└── expected/                     # 期望命中的 chunk 标注
    ├── expected-tech-report.json
    └── ...
```

#### 5.1.2 QA 标注格式

```json
[
  {
    "id": "qa-001",
    "doc_id": "tech-report.pdf",
    "question": "系统的并发处理能力是多少？",
    "answer": "系统支持最高 10000 QPS 的并发处理能力，在 8 核 CPU 下测得。",
    "relevant_chunk_ids": ["chunk-003", "chunk-004"],
    "relevant_paragraphs": [2],
    "difficulty": "easy",
    "category": "factual"
  },
  {
    "id": "qa-002",
    "doc_id": "tech-report.pdf",
    "question": "对比方案A和方案B的优缺点，哪个更适合高并发场景？",
    "answer": "方案A使用异步IO，延迟低但开发复杂；方案B使用线程池，简单但资源消耗大。高并发场景推荐方案A。",
    "relevant_chunk_ids": ["chunk-012", "chunk-015", "chunk-018"],
    "relevant_paragraphs": [5, 6],
    "difficulty": "hard",
    "category": "comparative"
  }
]
```

#### 5.1.3 标注规范

| 维度 | 说明 |
|------|------|
| 文档类型 | PDF、HTML、TXT、DOCX、XLSX 各 1-2 份 |
| QA 数量 | 每种文档 10-20 对，总计 50-100 对 |
| 难度分布 | easy 40%、medium 40%、hard 20% |
| 类型分布 | factual(事实查询) 50%、comparative(对比) 20%、summary(概括) 20%、multi-hop(多跳) 10% |
| 答案形式 | 尽量用原文片段，便于自动化评估 |

### 5.2 评估指标

| 指标 | 公式 | 含义 | 目标值 |
|------|------|------|--------|
| **Hit Rate** | hit_count / total_queries | 至少命中 1 个相关 chunk 的比例 | ≥ 90% |
| **Recall@K** | relevant_in_topK / total_relevant | topK 中覆盖的相关 chunk 比例 | ≥ 80% (K=5) |
| **Precision@K** | relevant_in_topK / K | topK 中相关 chunk 的占比 | ≥ 60% (K=5) |
| **MRR** | (1/N) * Σ(1/rank_i) | 第一个相关 chunk 排名的倒数均值 | ≥ 0.70 |
| **NDCG@K** | DCG@K / IDCG@K | 考虑排序位置的归一化指标 | ≥ 0.75 (K=5) |
| **Answer Coverage** | LLM 回答覆盖标准答案的比例 | 端到端可用性（可选） | ≥ 80% |

### 5.3 评估框架设计

```java
/**
 * RAG 评估引擎。
 * 用法：先索引文档 → 加载 QA 对 → 逐条检索 → 计算指标 → 输出报告。
 */
public class RagEvaluator {

    // ---- 核心方法 ----

    /** 运行完整评估 */
    public EvalReport evaluate(EvalConfig config) {
        // 1. 加载 QA 数据集
        List<QAPair> qaPairs = loadQAPairs(config.getQaDir());

        // 2. 索引文档（如果尚未索引）
        if (config.isReIndex()) {
            indexDocuments(config.getDocumentDir());
        }

        // 3. 逐条检索并计算
        List<QueryEvalResult> results = new ArrayList<>();
        for (QAPair qa : qaPairs) {
            QueryEvalResult result = evaluateSingle(qa, config.getTopK());
            results.add(result);
        }

        // 4. 汇总指标
        return buildReport(results, qaPairs.size());
    }

    /** 单条查询评估 */
    private QueryEvalResult evaluateSingle(QAPair qa, int topK) {
        long start = System.currentTimeMillis();

        // 执行检索
        List<RagHit> hits = ragService.search(
            RagNamespace.KNOWLEDGE, qa.getQuestion(), topK, MIN_SCORE
        );

        long latency = System.currentTimeMillis() - start;

        // 计算各项指标
        Set<String> hitIds = hits.stream().map(RagHit::getId).collect(toSet());
        Set<String> relevantIds = new HashSet<>(qa.getRelevantChunkIds());

        // Hit or Miss
        boolean hit = !Collections.disjoint(hitIds, relevantIds);

        // Recall@K
        long relevantCount = hits.stream().filter(h -> relevantIds.contains(h.getId())).count();
        double recall = (double) relevantCount / relevantIds.size();

        // MRR
        double rr = 0;
        for (int i = 0; i < hits.size(); i++) {
            if (relevantIds.contains(hits.get(i).getId())) {
                rr = 1.0 / (i + 1);
                break;
            }
        }

        return QueryEvalResult.builder()
            .qaId(qa.getId())
            .hit(hit)
            .recall(recall)
            .reciprocalRank(rr)
            .latencyMs(latency)
            .hits(hits)
            .build();
    }

    /** 生成评估报告 */
    private EvalReport buildReport(List<QueryEvalResult> results, int total) {
        long hitCount = results.stream().filter(QueryEvalResult::isHit).count();
        double avgRecall = results.stream().mapToDouble(QueryEvalResult::getRecall).average().orElse(0);
        double mrr = results.stream().mapToDouble(QueryEvalResult::getReciprocalRank).average().orElse(0);
        double avgLatency = results.stream().mapToDouble(QueryEvalResult::getLatencyMs).average().orElse(0);
        double p50Latency = percentile(results, 50);
        double p95Latency = percentile(results, 95);
        double p99Latency = percentile(results, 99);

        return EvalReport.builder()
            .totalQueries(total)
            .hitRate((double) hitCount / total)
            .avgRecallAtK(avgRecall)
            .mrr(mrr)
            .avgLatencyMs(avgLatency)
            .p50LatencyMs(p50Latency)
            .p95LatencyMs(p95Latency)
            .p99LatencyMs(p99Latency)
            .details(results)
            .build();
    }
}
```

### 5.4 对比实验设计

#### 5.4.1 实验矩阵

| 实验编号 | 索引策略 | Query 改写 | 检索方式 | Rerank | 上下文扩展 | 说明 |
|----------|----------|-----------|----------|--------|-----------|------|
| **Baseline** | 逐句分块 | 无 | 向量检索 | 无 | 无 | 当前方案 |
| **Exp-1** | 滑动窗口 | 无 | 向量检索 | 无 | 无 | 仅优化索引 |
| **Exp-2** | 滑动窗口 | 规则改写 | 向量检索 | 无 | 无 | +Query 改写 |
| **Exp-3** | 滑动窗口 | 规则改写 | 向量检索 | Cross-Encoder | 无 | +Rerank |
| **Exp-4** | 滑动窗口 | 规则改写 | 多路检索 | Cross-Encoder | 无 | +多路检索 |
| **Exp-5** | 滑动窗口+双粒度 | 规则改写 | 多路检索 | Cross-Encoder | 前后各1段 | **完整方案** |

#### 5.4.2 预估实验结果

> 基于业界公开数据和本项目的文本特征（中文技术文档为主）的合理估计。

```
┌──────────┬──────────┬──────────┬──────────┬──────────┬──────────┐
│   指标    │ Baseline │  Exp-1   │  Exp-2   │  Exp-3   │  Exp-5   │
│          │ (当前)   │ (+分块)  │ (+改写)  │ (+Rerank)│ (完整)   │
├──────────┼──────────┼──────────┼──────────┼──────────┼──────────┤
│ Hit Rate │  72%     │  85%     │  88%     │  90%     │  93%     │
│ Recall@5 │  58%     │  72%     │  76%     │  78%     │  85%     │
│ Prec@5   │  35%     │  48%     │  52%     │  62%     │  68%     │
│ MRR      │  0.52    │  0.65    │  0.69    │  0.75    │  0.80    │
│ NDCG@5   │  0.48    │  0.62    │  0.67    │  0.74    │  0.79    │
├──────────┼──────────┼──────────┼──────────┼──────────┼──────────┤
│ 延迟(ms) │  ~30ms   │  ~35ms   │  ~50ms   │ ~250ms   │ ~280ms   │
└──────────┴──────────┴──────────┴──────────┴──────────┴──────────┘

提升幅度（相对 Baseline）：
  Exp-1:  Hit Rate +13pp,  Recall@5 +14pp,  延迟 +5ms
  Exp-2:  Hit Rate +16pp,  Recall@5 +18pp,  延迟 +20ms
  Exp-3:  Hit Rate +18pp,  Recall@5 +20pp,  延迟 +220ms  ← 最大质量跃升
  Exp-5:  Hit Rate +21pp,  Recall@5 +27pp,  延迟 +250ms  ← 最优方案
```

### 5.5 评估报告输出示例

```
================================================================
  RAG 召回率评估报告
================================================================
评估时间: 2026-07-30 15:30:00
实验名称: Exp-5 (完整方案)
文档数量: 5
QA 对数量: 80
TopK: 5

---- 核心指标 ----
Hit Rate:       93.8%  (75/80)
Recall@5:       85.2%
Precision@5:    68.5%
MRR:            0.802
NDCG@5:         0.791

---- 延迟指标 ----
平均延迟:       278ms
P50 延迟:       245ms
P95 延迟:       520ms
P99 延迟:       680ms

---- 按难度分组 ----
Easy (n=32):    Hit Rate 100%,  Recall@5 94%
Medium (n=32):  Hit Rate 94%,   Recall@5 85%
Hard (n=16):    Hit Rate 75%,   Recall@5 65%

---- 按文档类型分组 ----
PDF:            Hit Rate 92%,  Recall@5 84%
HTML:           Hit Rate 95%,  Recall@5 87%
TXT:            Hit Rate 94%,  Recall@5 86%
DOCX:           Hit Rate 90%,  Recall@5 82%
XLSX:           Hit Rate 88%,  Recall@5 78%  ← 表格数据召回偏低

---- 失败案例分析 (Top 5) ----
1. qa-042 [hard/comparative] "对比三种方案的..."
   → 相关 chunk 排名: #8, #15 → 未进入 top5
   → 建议: 多跳推理需要更大 topK 或 query 拆解

2. qa-067 [hard/multi-hop] "方案A实施后对B模块的影响..."
   → 跨段落信息，单个 chunk 无法覆盖
   → 建议: 段落上下文扩展已部分缓解，但跨文档仍需优化
================================================================
```

---

## 六、实施路线图

### Phase 1：基础优化（1-2 天）

- [ ] `NlpUtils` 新增 `toChunks()` 滑动窗口方法
- [ ] `ChunkFilter` 噪声过滤
- [ ] `KnowledgeBuildService` 接入新分块逻辑
- [ ] metadata 增加 `paragraph_index`、`grain` 字段

### Phase 2：评估体系（1-2 天）

- [ ] 准备 5 份测试文档（PDF/HTML/TXT/DOCX/XLSX）
- [ ] 标注 50-80 对 QA
- [ ] 实现 `RagEvaluator` 评估框架
- [ ] 跑 Baseline 数据，建立基准

### Phase 3：检索增强（2-3 天）

- [ ] `QueryRewriter` 规则改写
- [ ] 段落上下文扩展逻辑
- [ ] `Reranker` 接口 + 规则 Rerank 实现
- [ ] 跑 Exp-1~Exp-4 对比实验

### Phase 4：深度优化（3-5 天）

- [ ] Cross-Encoder ONNX Rerank 集成
- [ ] 关键词倒排索引 + RRF 融合
- [ ] 双粒度索引（paragraph-level chunk 写入）
- [ ] LLM Query 改写（可选触发）
- [ ] 跑 Exp-5 完整方案，输出最终评估报告

---

## 七、附录：Rerank 模型 ONNX 部署要点

### 7.1 模型转换

```bash
# 从 HuggingFace 下载 bge-reranker-v2-m3
# 使用 optimum-cli 转换为 ONNX
optimum-cli export onnx \
  --model BAAI/bge-reranker-v2-m3 \
  --task text-classification \
  --opset 14 \
  ./models/bge-reranker-v2-m3-onnx
```

### 7.2 Java 集成

```java
// Maven 依赖
// ai.onnxruntime:onnxruntime:1.17.1

public class OnnxReranker implements Reranker {
    private final OrtEnvironment env;
    private final OrtSession session;
    private final BertTokenizer tokenizer;  // 需要引入 HuggingFace tokenizer

    public OnnxReranker(String modelPath) {
        this.env = OrtEnvironment.getEnvironment();
        this.session = env.createSession(modelPath, new OrtSession.SessionOptions());
    }

    public List<Float> rerank(String query, List<String> documents) {
        // 1. Tokenize: [CLS] query [SEP] doc [SEP]
        // 2. Batch 推理
        // 3. Sigmoid 输出分数
        // 4. 返回排序后的分数列表
    }
}
```

### 7.3 性能优化建议

- **INT8 量化**：模型从 568MB → ~150MB，推理耗时 -40%
- **线程数调优**：`SessionOptions.setIntraOpNumThreads(4)` 匹配物理核心
- **预热**：启动时跑一次 dummy 推理，避免首次调用冷启动
- **候选截断**：粗排取 2×topK（而非 3×），减少 Rerank 输入量

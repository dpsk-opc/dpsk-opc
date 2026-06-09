package com.xiaomizhou.dpsk.memory.impl;

import com.xiaomizhou.dpsk.memory.store.EmbeddingStore;
import io.github.jbellis.jvector.graph.*;
import io.github.jbellis.jvector.graph.similarity.BuildScoreProvider;
import io.github.jbellis.jvector.graph.similarity.DefaultSearchScoreProvider;
import io.github.jbellis.jvector.graph.similarity.SearchScoreProvider;
import io.github.jbellis.jvector.vector.DefaultVectorizationProvider;
import io.github.jbellis.jvector.vector.VectorSimilarityFunction;
import io.github.jbellis.jvector.vector.types.VectorFloat;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 基于 JVector 的向量存储实现（ANN + 磁盘持久化）。
 * <p>
 * 使用 JVector 3.x API 实现近似最近邻检索。
 * <b>当前状态：JVector 依赖已添加到 pom.xml，但需手动安装 jar 到本地仓库。</b>
 * <p>
 * 安装方式：
 * <pre>
 *   mvn dependency:get -Dartifact=io.github.jbellis:jvector:3.1.0
 * </pre>
 * 或手动下载 jar 后：
 * <pre>
 *   mvn install:install-file -Dfile=jvector-3.1.0.jar -DgroupId=io.github.jbellis -DartifactId=jvector -Dversion=3.1.0 -Dpackaging=jar
 * </pre>
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/29
 */
@Slf4j
public class JVectorEmbeddingStoreImpl implements EmbeddingStore {

    private static final int DEFAULT_DIMENSION = 1536;
    private static final int GRAPH_M = 32;
    private static final int GRAPH_CONSTRUCTION_BEAM_WIDTH = 100;
    private static final int GRAPH_SEARCH_BEAM_WIDTH = 200;
    private static final float GRAPH_ALPHA = 1.2f;

    private final List<VectorFloat<?>> vectors = new ArrayList<>();
    private final Map<Integer, DocEntry> entryMap = new ConcurrentHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final int dimension;
    private final Path persistenceDir;

    private volatile GraphSearcher searcher;
    private volatile boolean dirty = false;
    private int pendingAddCount = 0;
    private static final int REBUILD_THRESHOLD = 10;

    public JVectorEmbeddingStoreImpl() {
        this(DEFAULT_DIMENSION, null);
    }

    public JVectorEmbeddingStoreImpl(int dimension, String persistencePath) {
        this.dimension = dimension;
        this.persistenceDir = (persistencePath != null && !persistencePath.isEmpty())
                ? Paths.get(persistencePath) : null;
        log.info("JVectorEmbeddingStore initialized: dimension={}, persistenceDir={}", dimension, persistenceDir);
    }

    @Override
    public String add(String id, List<Float> vector, String text, Map<String, String> metadata) {
        VectorFloat<?> vf = toVectorFloat(vector);
        if (vf == null) return id;

        lock.writeLock().lock();
        try {
            int idx = vectors.size();
            vectors.add(vf);
            entryMap.put(idx, new DocEntry(id, text, metadata));
            dirty = true;
            pendingAddCount++;

            if (pendingAddCount >= REBUILD_THRESHOLD) {
                rebuildIndex();
            }
        } finally {
            lock.writeLock().unlock();
        }
        log.debug("Added embedding: id={}, total={}", id, vectors.size());
        return id;
    }

    @Override
    public List<EmbeddingMatch> search(List<Float> queryVector, int maxResults, double minScore) {
        VectorFloat<?> queryVf = toVectorFloat(queryVector);
        if (queryVf == null) return List.of();

        lock.readLock().lock();
        try {
            if (vectors.isEmpty()) return List.of();

            if (searcher != null && vectors.size() >= REBUILD_THRESHOLD) {

                return searchWithGraph(queryVf, maxResults, minScore);
            }
            return bruteForceSearch(queryVf, maxResults, minScore);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void delete(String id) {
        lock.writeLock().lock();
        try {
            entryMap.values().removeIf(e -> id.equals(e.id));
            dirty = true;
            pendingAddCount++;
            log.debug("Deleted embedding: id={}", id);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int size() {
        lock.readLock().lock();
        try {
            return entryMap.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    // ======================== 内部方法 ========================

    private VectorFloat<?> toVectorFloat(List<Float> list) {
        if (list == null || list.isEmpty()) return null;
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);

        return DefaultVectorizationProvider.getInstance().getVectorTypeSupport().createFloatVector(arr);
    }

    private List<EmbeddingMatch> searchWithGraph(VectorFloat<?> queryVf, int maxResults, double minScore) {
        try {


//            SearchScoreProvider scoreProvider = new DefaultSearchScoreProvider(VectorSimilarityFunction.COSINE, queryVf);
//            SearchScoreProvider scoreProvider = SearchScoreProvider.exact(queryVf, VectorSimilarityFunction.COSINE);
            List<VectorFloat<?>> ss = List.of(queryVf);
            // TODO 这里的1可能有问题，需要根据实际情况调整
            SearchScoreProvider scoreProvider = DefaultSearchScoreProvider.exact(queryVf, VectorSimilarityFunction.COSINE, new ListRandomAccessVectorValues(ss, 1));
            SearchResult result = searcher.search(
                    scoreProvider, maxResults * 2, GRAPH_SEARCH_BEAM_WIDTH,
                    (float) minScore, 0.0f, null);

            List<EmbeddingMatch> matches = new ArrayList<>();
            if (result.getNodes() != null) {
                for (SearchResult.NodeScore ns : result.getNodes()) {
                    if (ns == null) continue;
                    int idx = ns.node;
                    DocEntry entry = entryMap.get(idx);
                    if (entry != null && ns.score >= minScore) {
                        matches.add(new EmbeddingMatch(ns.score, entry.id, entry.text, entry.metadata));
                    }
                }
            }
            matches.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
            if (matches.size() > maxResults) return matches.subList(0, maxResults);
            return matches;
        } catch (Exception e) {
            log.warn("Graph search failed, falling back to brute force: {}", e.getMessage());
            return bruteForceSearch(queryVf, maxResults, minScore);
        }
    }

    private List<EmbeddingMatch> bruteForceSearch(VectorFloat<?> query, int maxResults, double minScore) {
        List<EmbeddingMatch> results = new ArrayList<>();
        float[] queryArr = toArray(query);
        for (int i = 0; i < vectors.size(); i++) {
            DocEntry entry = entryMap.get(i);
            if (entry == null) continue;
            double score = cosineSimilarity(queryArr, toArray(vectors.get(i)));
            if (score >= minScore) {
                results.add(new EmbeddingMatch(score, entry.id, entry.text, entry.metadata));
            }
        }
        results.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        if (results.size() > maxResults) return results.subList(0, maxResults);
        return results;
    }

    private void rebuildIndex() {
        if (vectors.isEmpty()) return;
        try {
            BuildScoreProvider bsp = BuildScoreProvider.randomAccessScoreProvider(
                    new ListRAVV(vectors), VectorSimilarityFunction.COSINE);
            GraphIndexBuilder builder = new GraphIndexBuilder(
                    bsp, dimension, GRAPH_M, GRAPH_CONSTRUCTION_BEAM_WIDTH,
                    GRAPH_ALPHA, 1.2f, false, false);
            var graphIndex = builder.build(new ListRAVV(vectors));
            this.searcher = new GraphSearcher(graphIndex);
            this.dirty = false;
            this.pendingAddCount = 0;
            log.debug("Graph index rebuilt: {} vectors", vectors.size());
        } catch (Exception e) {
            log.error("Failed to rebuild graph index: {}", e.getMessage());
            this.searcher = null;
        }
    }

    private float[] toArray(VectorFloat<?> vf) {
        float[] arr = new float[vf.length()];
        for (int i = 0; i < arr.length; i++) arr[i] = vf.get(i);
        return arr;
    }

    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) return 0.0;
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    // ======================== 内部类 ========================

    private static class DocEntry {
        final String id;
        final String text;
        final Map<String, String> metadata;

        DocEntry(String id, String text, Map<String, String> metadata) {
            this.id = id;
            this.text = text;
            this.metadata = new LinkedHashMap<>(metadata);
        }
    }

    /**
     * 将 List&lt;VectorFloat&gt; 适配为 JVector 的 RandomAccessVectorValues
     */
    private static class ListRAVV implements RandomAccessVectorValues {
        private final List<VectorFloat<?>> vecs;

        ListRAVV(List<VectorFloat<?>> vecs) {
            this.vecs = vecs;
        }

        @Override
        public int size() {
            return vecs.size();
        }

        @Override
        public int dimension() {
            return vecs.isEmpty() ? 0 : vecs.get(0).length();
        }

        @Override
        public VectorFloat<?> getVector(int i) {
            return vecs.get(i);
        }

        @Override
        public boolean isValueShared() {
            return false;
        }

        @Override
        public RandomAccessVectorValues copy() {
            return new ListRAVV(new ArrayList<>(vecs));
        }
    }
}

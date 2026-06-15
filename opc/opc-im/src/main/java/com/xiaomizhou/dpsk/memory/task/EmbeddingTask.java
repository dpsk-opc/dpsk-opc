package com.xiaomizhou.dpsk.memory.task;

import com.xiaomizhou.dpsk.memory.manager.FactManager;
import com.xiaomizhou.dpsk.memory.model.LongTermFact;
import com.xiaomizhou.dpsk.memory.repository.LongTermFactRepository;
import com.xiaomizhou.dpsk.memory.store.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 独立 Embedding 异步任务处理器。
 * <p>
 * 将向量化过程从 FactManager 主流程中解耦：
 * <ul>
 *   <li>FactManager 写入 t_long_term_fact 后，将 fact code 入队</li>
 *   <li>EmbeddingTask 后台消费队列，批量调用 Embedding API</li>
 *   <li>完成后写入向量存储</li>
 * </ul>
 * <p>
 * 优点：
 * <ul>
 *   <li>不阻塞对话回复生成</li>
 *   <li>支持批量处理（减少 API 调用次数）</li>
 *   <li>失败重试机制</li>
 * </ul>
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/29
 */
@Slf4j
public class EmbeddingTask implements Runnable {

    /** 待处理队列 */
    private final BlockingQueue<EmbeddingJob> queue = new LinkedBlockingQueue<>(1000);

    /** 批量处理大小 */
    private static final int BATCH_SIZE = 5;

    /** 队列空时等待时间 */
    private static final long POLL_TIMEOUT_MS = 3000;

    /** 最大重试次数 */
    private static final int MAX_RETRIES = 3;

    private final LongTermFactRepository factRepository;
    private final FactManager.EmbeddingClient embeddingClient;
    private final EmbeddingStore embeddingStore;

    private volatile boolean running = true;
    private final Thread workerThread;

    public EmbeddingTask(LongTermFactRepository factRepository,
                          FactManager.EmbeddingClient embeddingClient,
                          EmbeddingStore embeddingStore) {
        this.factRepository = factRepository;
        this.embeddingClient = embeddingClient;
        this.embeddingStore = embeddingStore;
        this.workerThread = new Thread(this, "embedding-task");
        this.workerThread.setDaemon(true);
        log.info("EmbeddingTask created");
    }

    /** 启动后台线程 */
    public void start() {
        if (!workerThread.isAlive()) {
            workerThread.start();
            log.info("EmbeddingTask worker thread started");
        }
    }

    /** 停止后台线程 */
    public void shutdown() {
        running = false;
        workerThread.interrupt();
        log.info("EmbeddingTask shutdown requested");
    }

    /**
     * 提交一个向量化任务。
     *
     * @param factCode         事实编码
     * @param conversationCode 会话编码
     */
    public void submit(String factCode, String conversationCode) {
        if (!queue.offer(new EmbeddingJob(factCode, conversationCode, 0))) {
            log.warn("Embedding queue full, dropped: factCode={}", factCode);
        }
    }

    @Override
    public void run() {
        log.info("EmbeddingTask worker loop started");
        while (running) {
            try {
                EmbeddingJob job = queue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (job == null) continue;

                // 收集一批任务
                EmbeddingJob[] batch = new EmbeddingJob[BATCH_SIZE];
                batch[0] = job;
                int count = 1;
                for (int i = 1; i < BATCH_SIZE; i++) {
                    EmbeddingJob next = queue.poll();
                    if (next == null) break;
                    batch[i] = next;
                    count++;
                }

                processBatch(batch, count);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("EmbeddingTask worker error", e);
            }
        }
        log.info("EmbeddingTask worker loop stopped");
    }

    /**
     * 批量处理向量化。
     */
    private void processBatch(EmbeddingJob[] batch, int count) {
        for (int i = 0; i < count; i++) {
            EmbeddingJob job = batch[i];
            if (job == null) continue;

            try {
                // 查询事实内容
                LongTermFact fact = factRepository.findByCode(job.factCode);
                if (fact == null) {
                    log.warn("Fact not found: code={}", job.factCode);
                    continue;
                }

                // 调用 Embedding API
                List<Float> vector = embeddingClient.embed(fact.getFactContent());
                if (vector == null || vector.isEmpty()) {
                    log.warn("Embedding failed for fact: code={}", job.factCode);
                    retryOrDrop(job);
                    continue;
                }

                // 构建元数据
                Map<String, String> metadata = new LinkedHashMap<>();
                metadata.put("type", "fact");
                metadata.put("owner_code", fact.getOwnerCode());
                metadata.put("target_code", fact.getTargetCode());
                metadata.put("conversation_code", job.conversationCode);
                metadata.put("timestamp", fact.getCreateTime().toString());
                metadata.put("importance", String.valueOf(fact.getImportance()));

                // 写入向量存储
                embeddingStore.add(vector, fact.getFactContent(), metadata);
                log.debug("Embedding stored: code={}, content={}", job.factCode,
                        fact.getFactContent().substring(0, Math.min(50, fact.getFactContent().length())));

            } catch (Exception e) {
                log.error("Failed to process embedding job: code={}", job.factCode, e);
                retryOrDrop(job);
            }
        }
    }

    /** 重试或丢弃 */
    private void retryOrDrop(EmbeddingJob job) {
        if (job.retryCount < MAX_RETRIES) {
            job.retryCount++;
            if (queue.offer(job)) {
                log.info("Requeued embedding job: code={}, retry={}", job.factCode, job.retryCount);
                return;
            }
        }
        log.warn("Dropped embedding job after {} retries: code={}", MAX_RETRIES, job.factCode);
    }

    // ======================== 内部类 ========================

    /** 向量化任务 */
    private static class EmbeddingJob {
        final String factCode;
        final String conversationCode;
        int retryCount;

        EmbeddingJob(String factCode, String conversationCode, int retryCount) {
            this.factCode = factCode;
            this.conversationCode = conversationCode;
            this.retryCount = retryCount;
        }
    }
}

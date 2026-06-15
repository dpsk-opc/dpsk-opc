package com.xiaomizhou.dpsk.task.consumer;

import com.xiaomizhou.dpsk.memory.manager.FactManager;
import com.xiaomizhou.dpsk.memory.store.EmbeddingStore;
import com.xiaomizhou.dpsk.task.KnowledgeBuildService;
import com.xiaomizhou.dpsk.task.model.Task;
import com.xiaomizhou.dpsk.task.model.TaskConsumeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 知识库构建任务消费者。
 * <p>
 * 定时任务触发，负责将知识库中已上传的文件解析、分块、向量化并存入 L3 向量存储。
 * <p>
 * Task.parameters（JSON）中可包含:
 * <ul>
 *   <li>{@code libCode} — 知识库编码</li>
 * </ul>
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/15
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KnowLedgeBuildTaskConsumer implements TaskConsumer {

    private static final String CONSUMER_KEY = "KNOWLEDGE_BUILD";

    private final KnowledgeBuildService knowledgeBuildService;
    private final EmbeddingStore embeddingStore;
    private final FactManager.EmbeddingClient embeddingClient;

    @Override
    public String getConsumerKey() {
        return CONSUMER_KEY;
    }

    @Override
    public TaskConsumeResult consume(Task task, Map<String, Object> triggerContext) {
        log.info("[KNOWLEDGE_BUILD] 任务触发: taskCode={}, taskName={}", task.getCode(), task.getName());

        try {

            KnowledgeBuildService.BuildResult result =
                    knowledgeBuildService.build(embeddingClient, embeddingStore);

            return toResult(result);

        } catch (Exception e) {
            log.error("[KNOWLEDGE_BUILD] 任务执行异常: taskCode={}", task.getCode(), e);
            return TaskConsumeResult.fail("任务执行异常: " + e.getMessage());
        }
    }

    private TaskConsumeResult toResult(KnowledgeBuildService.BuildResult result) {
        if (result.isSkipped()) {
            return TaskConsumeResult.ok("跳过: " + result.getMessage());
        }
        if (!result.isSuccess()) {
            return TaskConsumeResult.fail(result.getMessage());
        }
        return TaskConsumeResult.ok(String.format(
                "构建完成: 成功文件=%d, 失败文件=%d, 总分块=%d",
                result.getSuccessFiles(), result.getFailedFiles(), result.getTotalChunks()));
    }
}

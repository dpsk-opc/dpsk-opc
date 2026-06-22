package com.xiaomizhou.dpsk.task.consumer;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xiaomizhou.dpsk.constant.MessageStatus;
import com.xiaomizhou.dpsk.db.dao.ChatMessageDao;
import com.xiaomizhou.dpsk.db.model.ChatMessage;
import com.xiaomizhou.dpsk.task.model.Task;
import com.xiaomizhou.dpsk.task.model.TaskConsumeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 消息过期消费者：将超时的 THINKING / TOOL 类型消息标记为 IGNORE 状态。
 * <p>
 * 该 consumer 采用分批查询 + 按 ID 更新的策略，避免直接根据条件写 update SQL，
 * 以保障大批量更新时的可控性和安全性。
 *
 * @author eason - vipzhsh@163.com
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AiMessageExpiryConsumer implements TaskConsumer {

    public static final String MSG_EXPIRY_TASK_CONSUMER_KEY = "MSG_EXPIRY_TASK";

    /** 每批查询/更新的数量 */
    private static final int BATCH_SIZE = 200;

    private final ChatMessageDao chatMessageDao;

    /** 过期时间（分钟），可通过配置覆盖，默认 20 分钟 */
    @Value("${com.xiaomizhou.dpsk.opc.message.expiry.minutes:20}")
    private int expiryMinutes;

    @Override
    public String getConsumerKey() {
        return MSG_EXPIRY_TASK_CONSUMER_KEY;
    }

    @Override
    public TaskConsumeResult consume(Task task, Map<String, Object> triggerContext) {
        log.info("AiMessageExpiryConsumer start, expiryMinutes={}", expiryMinutes);

        Date expireBefore = computeExpireBefore(expiryMinutes);
        long totalIgnored = 0;
        Long lastId = null;

        try {
            while (true) {
                // 分批查询：按 ID 升序，每次查 BATCH_SIZE 条
                List<ChatMessage> batch = queryExpiredBatch(expireBefore, lastId);
                if (batch.isEmpty()) {
                    break;
                }

                // 提取本批 ID 列表
                List<Long> ids = batch.stream()
                        .map(ChatMessage::getId)
                        .collect(Collectors.toList());

                // 按 ID 精确更新
                boolean updated = batchUpdateStatusByIds(ids);
                if (updated) {
                    totalIgnored += ids.size();
                }

                // 记录本批最后一条的 ID，用于下一次分页
                lastId = ids.get(ids.size() - 1);

                log.debug("Batch ignored: ids={}, updated={}, lastId={}", ids.size(), updated, lastId);

                if (batch.size() < BATCH_SIZE) {
                    break;
                }
            }

            String result = String.format("Expired message cleanup completed, totalIgnored=%d", totalIgnored);
            log.info(result);
            return TaskConsumeResult.ok(result);

        } catch (Exception e) {
            log.error("AiMessageExpiryConsumer failed", e);
            return TaskConsumeResult.fail(e.getMessage());
        }
    }

    /**
     * 计算过期时间阈值。
     */
    private Date computeExpireBefore(int minutes) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(minutes);
        return Date.from(threshold.atZone(ZoneId.systemDefault()).toInstant());
    }

    /**
     * 分批查询需要过期的消息。
     * <p>
     * 条件：message_type IN ('THINKING', 'TOOL') AND status != 'IGNORE' AND create_time < expireBefore
     * 按 ID 升序排序，支持基于 lastId 的分页。
     *
     * @param expireBefore 过期时间阈值
     * @param lastId       上一批最后一条记录的 ID（首次为 null）
     * @return 本批消息列表
     */
    private List<ChatMessage> queryExpiredBatch(Date expireBefore, Long lastId) {
        var query = Wrappers.<ChatMessage>lambdaQuery()
                .select(ChatMessage::getId)
                .in(ChatMessage::getMessageType, "THINKING", "TOOL")
                .ne(ChatMessage::getStatus, MessageStatus.IGNORED)
                .lt(ChatMessage::getCreateTime, expireBefore)
                .orderByAsc(ChatMessage::getId)
                .last("limit " + BATCH_SIZE);

        if (lastId != null) {
            query.gt(ChatMessage::getId, lastId);
        }

        return chatMessageDao.list(query);
    }

    /**
     * 根据 ID 列表批量更新状态为 IGNORE。
     *
     * @param ids 消息 ID 列表
     * @return 实际更新的行数
     */
    private boolean batchUpdateStatusByIds(List<Long> ids) {
        var update = Wrappers.<ChatMessage>lambdaUpdate()
                .set(ChatMessage::getStatus, MessageStatus.IGNORED)
                .set(ChatMessage::getUpdateTime, new Date())
                .in(ChatMessage::getId, ids);

        return chatMessageDao.update(update);
    }
}

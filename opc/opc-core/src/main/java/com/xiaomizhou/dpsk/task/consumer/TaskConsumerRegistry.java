package com.xiaomizhou.dpsk.task.consumer;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务消费者注册表。
 *
 * @author eason - vipzhsh@163.com
 */
@Slf4j
public class TaskConsumerRegistry {

    private final Map<String, TaskConsumer> consumers = new ConcurrentHashMap<>();

    /**
     * 注册消费者。
     */
    public void register(TaskConsumer consumer) {
        TaskConsumer existing = consumers.putIfAbsent(consumer.getConsumerKey(), consumer);
        if (existing != null) {
            log.warn("Consumer key '{}' already registered, skipping duplicate", consumer.getConsumerKey());
        } else {
            log.info("Consumer registered: {}", consumer.getConsumerKey());
        }
    }

    /**
     * 获取消费者，不存在则返回 null。
     */
    public TaskConsumer getConsumer(String key) {
        return consumers.get(key);
    }

    /**
     * 检查消费者是否已注册。
     */
    public boolean hasConsumer(String key) {
        return consumers.containsKey(key);
    }
}

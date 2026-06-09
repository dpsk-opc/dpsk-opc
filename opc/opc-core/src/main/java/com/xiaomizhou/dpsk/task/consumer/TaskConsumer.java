package com.xiaomizhou.dpsk.task.consumer;

import com.xiaomizhou.dpsk.task.model.Task;
import com.xiaomizhou.dpsk.task.model.TaskConsumeResult;

import java.util.Map;

/**
 * 任务消费者接口。
 * <p>
 * 业务方实现此接口并通过 TaskConsumerRegistry 注册，即可接收任务触发。
 *
 * @author eason - vipzhsh@163.com
 */
public interface TaskConsumer {

    /**
     * 消费者标识，用于路由。
     * 必须全局唯一。
     */
    String getConsumerKey();

    /**
     * 消费任务。
     *
     * @param task           任务定义
     * @param triggerContext 触发上下文（可为空）
     * @return 执行结果
     */
    TaskConsumeResult consume(Task task, Map<String, Object> triggerContext);
}

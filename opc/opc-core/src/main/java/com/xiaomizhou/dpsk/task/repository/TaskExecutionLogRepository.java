package com.xiaomizhou.dpsk.task.repository;

import com.xiaomizhou.dpsk.task.model.TaskExecutionLog;

import java.util.List;

/**
 * 任务执行日志仓储接口。
 * <p>
 * opc-core 仅定义接口，具体实现由 opc-im 基于 MyBatis-Plus 提供。
 *
 * @author eason - vipzhsh@163.com
 */
public interface TaskExecutionLogRepository {

    /** 保存执行日志 */
    void save(TaskExecutionLog log);

    /** 更新执行日志 */
    void update(TaskExecutionLog log);

    /** 根据 ID 查询执行日志 */
    TaskExecutionLog findByLogId(Long logId);

    /** 根据任务编码查询执行日志（按时间降序） */
    List<TaskExecutionLog> findByTaskCode(String taskCode, int limit);

    /** 根据消费者标识查询执行日志（按时间降序） */
    List<TaskExecutionLog> findByConsumerKey(String consumerKey, int limit);
}

package com.xiaomizhou.dpsk.task.repository;

import com.xiaomizhou.dpsk.task.model.Task;

import java.util.List;

/**
 * 任务仓储接口。
 * <p>
 * opc-core 仅定义接口，具体实现由 opc-im 基于 MyBatis-Plus 提供。
 *
 * @author eason - vipzhsh@163.com
 */
public interface TaskRepository {

    /** 根据编码查询 */
    Task findByCode(String code);

    /** 查询所有启用的定时任务 */
    List<Task> findAllEnabledScheduled();

    /** 查询所有启用的任务 */
    List<Task> findAllEnabled();

    /** 按类型查询 */
    List<Task> findByTaskType(String taskType);

    /** 保存任务 */
    void save(Task task);

    /** 更新任务 */
    void update(Task task);

    /** 根据编码更新状态 */
    void updateStatus(String code, String status);

    /** 根据编码逻辑删除 */
    void deleteByCode(String code);

    /** 检查编码是否存在 */
    boolean existsByCode(String code);
}

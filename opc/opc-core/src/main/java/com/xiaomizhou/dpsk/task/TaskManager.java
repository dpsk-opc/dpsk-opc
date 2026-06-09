package com.xiaomizhou.dpsk.task;

import com.xiaomizhou.dpsk.task.model.Task;
import com.xiaomizhou.dpsk.task.model.TaskConsumeResult;

import java.util.List;
import java.util.Map;

/**
 * 任务管理器接口，提供任务的统一操作入口。
 *
 * @author eason - vipzhsh@163.com
 */
public interface TaskManager {

    /** 创建任务，返回任务编码 */
    String createTask(Task task);

    /** 更新任务 */
    void updateTask(Task task);

    /** 根据编码删除任务（逻辑删除） */
    void deleteTask(String code);

    /** 启用任务 */
    void enableTask(String code);

    /** 禁用任务 */
    void disableTask(String code);

    /** 手动触发任务 */
    TaskConsumeResult triggerTask(String code, String triggerType, Map<String, Object> triggerContext);

    /** 根据编码查询任务 */
    Task getTask(String code);

    /** 查询所有启用的任务 */
    List<Task> listEnabledTasks();

    /** 启动调度器和加载所有定时任务 */
    void start();

    /** 启动时加载所有定时任务到调度器 */
    void loadAllScheduledTasksOnStartup();

    /** 停止任务引擎（关闭线程池和调度器） */
    void stop();
}

package com.xiaomizhou.dpsk.task;

import com.xiaomizhou.dpsk.db.dao.TaskExecutionLogDao;
import com.xiaomizhou.dpsk.db.model.TaskExecutionLogDO;
import com.xiaomizhou.dpsk.task.model.TaskExecutionLog;
import com.xiaomizhou.dpsk.task.repository.TaskExecutionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * TaskExecutionLogRepository 实现，基于 MyBatis-Plus。
 *
 * @author eason - vipzhsh@163.com
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TaskExecutionLogRepositoryImpl implements TaskExecutionLogRepository {

    private final TaskExecutionLogDao taskExecutionLogDao;

    @Override
    public void save(TaskExecutionLog log) {
        TaskExecutionLogDO entity = toDbModel(log);
        taskExecutionLogDao.save(entity);
        // 回填 ID
        log.setId(entity.getId());
    }

    @Override
    public void update(TaskExecutionLog log) {
        TaskExecutionLogDO entity = toDbModel(log);
        taskExecutionLogDao.updateById(entity);
    }

    @Override
    public TaskExecutionLog findByLogId(Long logId) {
        TaskExecutionLogDO entity = taskExecutionLogDao.getById(logId);
        return toCoreModel(entity);
    }

    @Override
    public List<TaskExecutionLog> findByTaskCode(String taskCode, int limit) {
        return taskExecutionLogDao.findByTaskCode(taskCode, limit).stream()
                .map(this::toCoreModel)
                .collect(Collectors.toList());
    }

    @Override
    public List<TaskExecutionLog> findByConsumerKey(String consumerKey, int limit) {
        return taskExecutionLogDao.findByConsumerKey(consumerKey, limit).stream()
                .map(this::toCoreModel)
                .collect(Collectors.toList());
    }

    // ---- 模型转换 ----

    private TaskExecutionLog toCoreModel(TaskExecutionLogDO entity) {
        if (entity == null) return null;
        return TaskExecutionLog.builder()
                .id(entity.getId())
                .taskCode(entity.getTaskCode())
                .consumerKey(entity.getConsumerKey())
                .triggerType(entity.getTriggerType())
                .status(entity.getStatus())
                .startTime(entity.getStartTime())
                .endTime(entity.getEndTime())
                .durationMs(entity.getDurationMs())
                .result(entity.getResult())
                .errorMessage(entity.getErrorMessage())
                .build();
    }

    private TaskExecutionLogDO toDbModel(TaskExecutionLog model) {
        TaskExecutionLogDO entity = new TaskExecutionLogDO();
        entity.setId(model.getId());
        entity.setTaskCode(model.getTaskCode());
        entity.setConsumerKey(model.getConsumerKey());
        entity.setTriggerType(model.getTriggerType());
        entity.setStatus(model.getStatus());
        entity.setStartTime(model.getStartTime());
        entity.setEndTime(model.getEndTime());
        entity.setDurationMs(model.getDurationMs());
        entity.setResult(model.getResult());
        entity.setErrorMessage(model.getErrorMessage());
        entity.setUpdateTime(new Date());
        entity.setCreateTime(new Date());
        return entity;
    }
}

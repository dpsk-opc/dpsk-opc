package com.xiaomizhou.dpsk.task;

import com.xiaomizhou.dpsk.db.dao.TaskDao;
import com.xiaomizhou.dpsk.db.model.TaskDO;
import com.xiaomizhou.dpsk.task.model.Task;
import com.xiaomizhou.dpsk.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * TaskRepository 实现，基于 MyBatis-Plus。
 *
 * @author eason - vipzhsh@163.com
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TaskRepositoryImpl implements TaskRepository {

    private final TaskDao taskDao;

    @Override
    public Task findByCode(String code) {
        return toCoreModel(taskDao.getByCode(code));
    }

    @Override
    public List<Task> findAllEnabledScheduled() {
        return taskDao.findAllEnabledScheduled().stream()
                .map(this::toCoreModel)
                .collect(Collectors.toList());
    }

    @Override
    public List<Task> findAllEnabled() {
        return taskDao.findAllEnabled().stream()
                .map(this::toCoreModel)
                .collect(Collectors.toList());
    }

    @Override
    public List<Task> findByTaskType(String taskType) {
        return taskDao.findByTaskType(taskType).stream()
                .map(this::toCoreModel)
                .collect(Collectors.toList());
    }

    @Override
    public void save(Task task) {
        TaskDO entity = toDbModel(task);
        if (task.getId() != null) {
            taskDao.updateById(entity);
        } else {
            TaskDO existing = taskDao.getByCode(task.getCode());
            if (existing != null) {
                entity.setId(existing.getId());
                entity.setUpdateTime(new Date());
                taskDao.updateById(entity);
            } else {
                entity.setUpdateTime(new Date());
                entity.setCreateTime(new Date());
                taskDao.save(entity);
            }
        }
    }

    @Override
    public void update(Task task) {
        TaskDO entity = toDbModel(task);
        TaskDO existing = taskDao.getByCode(task.getCode());
        if (existing != null) {
            entity.setId(existing.getId());
        }
        entity.setUpdateTime(new Date());
        taskDao.updateById(entity);
    }

    @Override
    public void updateStatus(String code, String status) {
        taskDao.updateStatus(code, status);
    }

    @Override
    public void deleteByCode(String code) {
        taskDao.deleteByCode(code);
    }

    @Override
    public boolean existsByCode(String code) {
        return taskDao.existsByCode(code);
    }

    // ---- 模型转换 ----

    private Task toCoreModel(TaskDO entity) {
        if (entity == null) return null;
        return Task.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .name(entity.getName())
                .taskType(entity.getTaskType())
                .status(entity.getStatus())
                .consumerKey(entity.getConsumerKey())
                .parameters(entity.getParameters())
                .agentCode(entity.getAgentCode())
                .conversationCode(entity.getConversationCode())
                .build();
    }

    private TaskDO toDbModel(Task model) {
        TaskDO entity = new TaskDO();
        entity.setId(model.getId());
        entity.setCode(model.getCode());
        entity.setName(model.getName());
        entity.setTaskType(model.getTaskType());
        entity.setStatus(model.getStatus());
        entity.setConsumerKey(model.getConsumerKey());
        entity.setParameters(model.getParameters());
        entity.setAgentCode(model.getAgentCode());
        entity.setConversationCode(model.getConversationCode());
        return entity;
    }
}

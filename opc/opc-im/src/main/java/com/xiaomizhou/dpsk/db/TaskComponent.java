package com.xiaomizhou.dpsk.db;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiaomizhou.dpsk.db.dao.TaskDao;
import com.xiaomizhou.dpsk.db.dao.TaskExecutionLogDao;
import com.xiaomizhou.dpsk.db.dto.*;
import com.xiaomizhou.dpsk.db.model.TaskDO;
import com.xiaomizhou.dpsk.db.model.TaskExecutionLogDO;
import com.xiaomizhou.dpsk.core.exceptions.BusinessException;
import com.xiaomizhou.dpsk.task.TaskManager;
import com.xiaomizhou.dpsk.task.model.Task;
import com.xiaomizhou.dpsk.task.model.TaskConsumeResult;
import com.xiaomizhou.dpsk.task.model.TaskExecutionLog;
import com.xiaomizhou.dpsk.utils.SequenceUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.xiaomizhou.dpsk.utils.SequenceUtils.UUIDSequenceGenerator.TASK_PREFIX;

/**
 * 任务业务处理类。
 *
 * @author eason - vipzhsh@163.com
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TaskComponent {

    private final TaskManager taskManager;
    private final TaskDao taskDao;
    private final TaskExecutionLogDao taskExecutionLogDao;

    private final AgentComponent agentComponent;
    /**
     * 创建任务。
     */
    @Transactional(rollbackFor = Exception.class)
    public TaskDto create(TaskCreateCmd cmd) {
        String code = SequenceUtils.generator().next(TASK_PREFIX);

        Task task = Task.builder()
                .code(code)
                .name(cmd.getName())
                .taskType(cmd.getTaskType())
                .status(Task.STATUS_ENABLED)
                .consumerKey(cmd.getConsumerKey())
                .parameters(cmd.getParameters())
                .agentCode(cmd.getAgentCode())
                .conversationCode(cmd.getConversationCode())
                .build();

        String createdCode = taskManager.createTask(task);
        log.info("创建任务成功, code={}", createdCode);

        return convertToDto(taskManager.getTask(createdCode));
    }

    /**
     * 更新任务。
     */
    @Transactional(rollbackFor = Exception.class)
    public TaskDto update(TaskUpdateCmd cmd) {
        Task existing = taskManager.getTask(cmd.getCode());
        if (existing == null) {
            throw BusinessException.notFound("任务不存在, code=" + cmd.getCode());
        }

        // 合并更新
        Task.TaskBuilder builder = Task.builder()
                .code(cmd.getCode());

        // 只更新非空字段
        if (cmd.getName() != null) builder.name(cmd.getName());
        else builder.name(existing.getName());

        if (cmd.getTaskType() != null) builder.taskType(cmd.getTaskType());
        else builder.taskType(existing.getTaskType());

        builder.status(existing.getStatus());

        if (cmd.getConsumerKey() != null) builder.consumerKey(cmd.getConsumerKey());
        else builder.consumerKey(existing.getConsumerKey());

        if (cmd.getParameters() != null) builder.parameters(cmd.getParameters());
        else builder.parameters(existing.getParameters());

        if (cmd.getAgentCode() != null) builder.agentCode(cmd.getAgentCode());
        else builder.agentCode(existing.getAgentCode());

        if (cmd.getConversationCode() != null) builder.conversationCode(cmd.getConversationCode());
        else builder.conversationCode(existing.getConversationCode());

        Task task = builder.build();
        taskManager.updateTask(task);
        log.info("更新任务成功, code={}", cmd.getCode());

        return convertToDto(taskManager.getTask(cmd.getCode()));
    }

    /**
     * 删除任务（逻辑删除）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String code) {
        Task task = taskManager.getTask(code);
        if (task == null) {
            return;
        }
        taskManager.deleteTask(code);
        log.info("删除任务成功, code={}", code);
    }

    /**
     * 启用任务。
     */
    @Transactional(rollbackFor = Exception.class)
    public void enable(String code) {
        taskManager.enableTask(code);
        log.info("启用任务成功, code={}", code);
    }

    /**
     * 禁用任务。
     */
    @Transactional(rollbackFor = Exception.class)
    public void disable(String code) {
        taskManager.disableTask(code);
        log.info("禁用任务成功, code={}", code);
    }

    /**
     * 手动触发任务。
     */
    public TaskConsumeResult trigger(TaskTriggerParam param) {
        String triggerType = StringUtils.defaultIfBlank(param.getTriggerType(), TaskExecutionLog.TRIGGER_MANUAL);
        Map<String, Object> ctx = param.getTriggerContext();
        return taskManager.triggerTask(param.getCode(), triggerType, ctx);
    }

    /**
     * 根据编码查询。
     */
    public TaskDto getByCode(String code) {
        Task task = taskManager.getTask(code);
        TaskDto dto = convertToDto(task);
        if (Objects.isNull(dto)) {
            return null;
        }

        AgentDto agent = agentComponent.getByCode(dto.getAgentCode());
        if (Objects.nonNull(agent)) {
            agent.setLlmConfig("");
        }
        dto.setAgent(agent);
        return dto;
    }

    /**
     * 分页查询任务。
     */
    public ImmutablePair<Long, List<TaskDto>> queryPage(TaskQueryParam param) {
        LambdaQueryWrapper<TaskDO> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.isNotBlank(param.getTaskType())) {
            wrapper.eq(TaskDO::getTaskType, param.getTaskType());
        }
        if (StringUtils.isNotBlank(param.getStatus())) {
            wrapper.eq(TaskDO::getStatus, param.getStatus());
        }
        if (StringUtils.isNotBlank(param.getConsumerKey())) {
            wrapper.eq(TaskDO::getConsumerKey, param.getConsumerKey());
        }
        if (StringUtils.isNotBlank(param.getAgentCode())) {
            wrapper.eq(TaskDO::getAgentCode, param.getAgentCode());
        }
        if (StringUtils.isNotBlank(param.getKeyword())) {
            wrapper.like(TaskDO::getName, param.getKeyword());
        }
        // 排除已删除
        wrapper.eq(TaskDO::getIsDeleted, 0);

        long cnt = taskDao.count(wrapper);
        if (cnt == 0) {
            return ImmutablePair.of(0L, List.of());
        }

        int pageNo = param.getPageNo() != null && param.getPageNo() > 0 ? param.getPageNo() : 1;
        int pageSize = param.getPageSize() != null && param.getPageSize() > 0 ? param.getPageSize() : 10;

        List<TaskDO> list = taskDao.list(
                wrapper.last("limit %s,%s".formatted((pageNo - 1) * pageSize, pageSize))
                        .orderByDesc(TaskDO::getCreateTime));

        Set<String> agentCodes = list.stream().map(TaskDO::getAgentCode).collect(Collectors.toSet());
        Map<String, AgentDto> agentMap = agentComponent.getByCodes(agentCodes).stream().collect(Collectors.toMap(AgentDto::getCode, Function.identity()));

        List<TaskDto> dtos = list.stream().map(this::convertToDto).toList();
        dtos.forEach(dto -> {
            if (agentMap.containsKey(dto.getAgentCode())) {
                AgentDto agent = agentMap.get(dto.getAgentCode());
                if (Objects.nonNull(agent)) {
                    agent.setLlmConfig("");
                }
                dto.setAgent(agent);
            }
        });
        return ImmutablePair.of(cnt, dtos);
    }

    /**
     * 分页查询执行日志。
     */
    public ImmutablePair<Long, List<TaskExecutionLogDto>> queryExecutionLogPage(TaskExecutionLogQueryParam param,int pageNo,int pageSize) {
        LambdaQueryWrapper<TaskExecutionLogDO> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.isNotBlank(param.getTaskCode())) {
            wrapper.eq(TaskExecutionLogDO::getTaskCode, param.getTaskCode());
        }
        if (StringUtils.isNotBlank(param.getConsumerKey())) {
            wrapper.eq(TaskExecutionLogDO::getConsumerKey, param.getConsumerKey());
        }
        if (StringUtils.isNotBlank(param.getStatus())) {
            wrapper.eq(TaskExecutionLogDO::getStatus, param.getStatus());
        }
        wrapper.eq(TaskExecutionLogDO::getIsDeleted, 0);

        long cnt = taskExecutionLogDao.count(wrapper);
        if (cnt == 0) {
            return ImmutablePair.of(0L, List.of());
        }


        List<TaskExecutionLogDO> list = taskExecutionLogDao.list(
                wrapper.last("limit %s,%s".formatted((pageNo - 1) * pageSize, pageSize))
                        .orderByDesc(TaskExecutionLogDO::getStartTime));

        List<TaskExecutionLogDto> dtos = list.stream().map(this::convertLogToDto).toList();
        return ImmutablePair.of(cnt, dtos);
    }

    // ---- 模型转换 ----

    private TaskDto convertToDto(Task task) {
        if (task == null) return null;
        TaskDto dto = new TaskDto();
        dto.setId(task.getId());
        dto.setCode(task.getCode());
        dto.setName(task.getName());
        dto.setTaskType(task.getTaskType());
        dto.setStatus(task.getStatus());
        dto.setConsumerKey(task.getConsumerKey());
        dto.setParameters(task.getParameters());
        dto.setAgentCode(task.getAgentCode());
        dto.setConversationCode(task.getConversationCode());
        return dto;
    }

    private TaskDto convertToDto(TaskDO entity) {
        if (entity == null) return null;
        TaskDto dto = new TaskDto();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setTaskType(entity.getTaskType());
        dto.setStatus(entity.getStatus());
        dto.setConsumerKey(entity.getConsumerKey());
        dto.setParameters(entity.getParameters());
        dto.setAgentCode(entity.getAgentCode());
        dto.setConversationCode(entity.getConversationCode());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setCreateTime(entity.getCreateTime());
        return dto;
    }

    private TaskExecutionLogDto convertLogToDto(TaskExecutionLogDO entity) {
        if (entity == null) return null;
        TaskExecutionLogDto dto = new TaskExecutionLogDto();
        dto.setId(entity.getId());
        dto.setTaskCode(entity.getTaskCode());
        dto.setConsumerKey(entity.getConsumerKey());
        dto.setTriggerType(entity.getTriggerType());
        dto.setStatus(entity.getStatus());
        dto.setStartTime(entity.getStartTime());
        dto.setEndTime(entity.getEndTime());
        dto.setDurationMs(entity.getDurationMs());
        dto.setResult(entity.getResult());
        dto.setErrorMessage(entity.getErrorMessage());
        return dto;
    }
}

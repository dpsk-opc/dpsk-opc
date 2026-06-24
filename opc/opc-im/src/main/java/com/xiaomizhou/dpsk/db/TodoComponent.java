package com.xiaomizhou.dpsk.db;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.common.collect.Maps;
import com.xiaomizhou.dpsk.core.exceptions.BusinessException;
import com.xiaomizhou.dpsk.db.dao.TodoItemDao;
import com.xiaomizhou.dpsk.db.dto.*;
import com.xiaomizhou.dpsk.db.model.TodoItemDO;
import com.xiaomizhou.dpsk.task.consumer.AgentTodoConsumer;
import com.xiaomizhou.dpsk.task.model.Task;
import com.xiaomizhou.dpsk.utils.JsonUtils;
import com.xiaomizhou.dpsk.utils.SequenceUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 待办事项业务处理类。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/24
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TodoComponent {

    /** 待办编码前缀 */
    private static final String TODO_PREFIX = "TODO";


    private final TodoItemDao todoItemDao;

    private final TaskComponent taskComponent;

    /**
     * 创建待办事项。
     */
    @Transactional(rollbackFor = Exception.class)
    public TodoItemDto create(TodoCreateCmd cmd, String ownerCode) {
        String code = SequenceUtils.generator().next(TODO_PREFIX);

        TodoItemDO entity = new TodoItemDO();
        entity.setCode(code);
        entity.setAgentCode(cmd.getAgentId());
        entity.setOwnerCode(ownerCode);
        entity.setTitle(cmd.getTitle());
        entity.setContent(cmd.getContent());
        entity.setDueTime(cmd.getDueTime());
        entity.setAlarmSound(cmd.getAlarmSound());
        entity.setAlarmEnabled(cmd.getAlarmEnabled() != null && cmd.getAlarmEnabled() ? 1 : 0);
        entity.setStatus(cmd.getStatus());
        entity.setConversationCode(code);
        entity.setCreateTime(new Date());
        entity.setUpdateTime(new Date());

        todoItemDao.save(entity);

        if (cmd.getAlarmEnabled()) {


            Map<String, Object> map = Maps.newHashMap();

            map.put("todo_code", code);
            map.put("cron", dateToCron(cmd.getDueTime()));
            map.put("agent_code", cmd.getAgentId());
            map.put("conversation_code", cmd.getConversationCode());

            TaskCreateCmd task = new TaskCreateCmd();
            task.setSource(Task.SOURCE_USER);
            task.setTaskType(Task.TYPE_TODO);
            task.setName(cmd.getTitle());
            task.setParameters(JsonUtils.toJson(map));
            task.setAgentCode(cmd.getAgentId());
            task.setConsumerKey(AgentTodoConsumer.CONSUMER_KEY);

            TaskDto dto = taskComponent.create(task);
            todoItemDao.lambdaUpdate().set(TodoItemDO::getTaskCode, dto.getCode()).eq(TodoItemDO::getCode, code).update();
        }

        log.info("创建待办成功: code={}, title={}", code, cmd.getTitle());
        return convertToDto(entity);
    }

    /**
     * 更新待办事项。
     */
    @Transactional(rollbackFor = Exception.class)
    public TodoItemDto update(TodoUpdateCmd cmd) {
        TodoItemDO existing = todoItemDao.getOne(
                new LambdaQueryWrapper<TodoItemDO>()
                        .eq(TodoItemDO::getCode, cmd.getId())
                        .eq(TodoItemDO::getIsDeleted, 0));
        if (existing == null) {
            throw BusinessException.notFound("待办不存在: " + cmd.getId());
        }

        if (StringUtils.isNotBlank(cmd.getTitle())) {
            existing.setTitle(cmd.getTitle());
        }
        if (StringUtils.isNotBlank(cmd.getContent())) {
            existing.setContent(cmd.getContent());
        }
        if (Objects.nonNull(cmd.getDueTime())) {
            existing.setDueTime(cmd.getDueTime());
        }
        if (cmd.getAlarmSound() != null) {
            existing.setAlarmSound(cmd.getAlarmSound());
        }
        if (cmd.getAlarmEnabled() != null) {
            existing.setAlarmEnabled(cmd.getAlarmEnabled() ? 1 : 0);
        }
        if (Objects.nonNull(cmd.getStatus())) {
            existing.setStatus(cmd.getStatus());
        }
        existing.setUpdateTime(new Date());

        todoItemDao.updateById(existing);

        if(cmd.getAlarmEnabled()){



            Map<String, Object> map = Maps.newHashMap();

            map.put("todo_code", existing.getCode());
            map.put("cron", dateToCron(cmd.getDueTime()));
            map.put("agent_code", existing.getAgentCode());
            map.put("conversation_code", existing.getConversationCode());

            TaskUpdateCmd task = new TaskUpdateCmd();

            task.setCode(existing.getTaskCode());
            task.setParameters(JsonUtils.toJson(map));

            taskComponent.update(task);
        }

        log.info("更新待办成功: code={}", cmd.getId());

        return convertToDto(existing);
    }

    /**
     * 删除待办（逻辑删除）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String code) {

        TodoItemDO todoItemDO = todoItemDao.getOne(
                new LambdaQueryWrapper<TodoItemDO>()
                        .eq(TodoItemDO::getCode, code)
                        .eq(TodoItemDO::getIsDeleted, 0));

        if (Objects.isNull(todoItemDO)) {
            return;
        }

        todoItemDao.removeById(todoItemDO.getId());

        if (StringUtils.isNotBlank(todoItemDO.getTaskCode())) {
            taskComponent.delete(todoItemDO.getTaskCode());
        }
        log.info("删除待办成功: code={}", code);
    }

    /**
     * 根据编码查询待办。
     */
    public TodoItemDto getByCode(String code) {
        TodoItemDO entity = todoItemDao.getOne(
                new LambdaQueryWrapper<TodoItemDO>()
                        .eq(TodoItemDO::getCode, code)
                        .eq(TodoItemDO::getIsDeleted, 0));
        return convertToDto(entity);
    }

    /**
     * 按联系人分页查询待办列表。
     *
     * @param agentCode 联系人 Agent Code（必填）
     * @param status    状态筛选（null=全部）
     * @param pageNo    页码（从 1 开始）
     * @param pageSize  每页条数
     */
    public ImmutablePair<Long, List<TodoItemDto>> pageByAgent(String agentCode, Integer status, int pageNo, int pageSize) {
        LambdaQueryWrapper<TodoItemDO> wrapper = new LambdaQueryWrapper<TodoItemDO>()
                .eq(TodoItemDO::getAgentCode, agentCode)
                .eq(TodoItemDO::getIsDeleted, 0);

        if (Objects.nonNull(status)) {
            wrapper.eq(TodoItemDO::getStatus, status);
        }

        long cnt = todoItemDao.count(wrapper);
        if (cnt == 0) {
            return ImmutablePair.of(0L, List.of());
        }

        int pn = pageNo > 0 ? pageNo : 1;
        int ps = pageSize > 0 ? pageSize : 10;

        List<TodoItemDO> list = todoItemDao.list(
                wrapper.last("limit %s,%s".formatted((pn - 1) * ps, ps))
                        .orderByAsc(TodoItemDO::getDueTime)
                        .orderByDesc(TodoItemDO::getCreateTime));

        List<TodoItemDto> dtos = list.stream().map(this::convertToDto).toList();
        return ImmutablePair.of(cnt, dtos);
    }

    // ======================== 模型转换 ========================

    private TodoItemDto convertToDto(TodoItemDO entity) {
        if (entity == null) return null;

        TodoItemDto dto = new TodoItemDto();
        dto.setId(entity.getCode());
        dto.setAgentId(entity.getAgentCode());
        dto.setTitle(entity.getTitle());
        dto.setContent(entity.getContent());
        dto.setDueTime(entity.getDueTime());
        dto.setAlarmSound(entity.getAlarmSound());
        dto.setAlarmEnabled(entity.getAlarmEnabled() != null && entity.getAlarmEnabled() == 1);
        dto.setStatus(entity.getStatus());
        dto.setCreateTime(new Date());
        dto.setUpdateTime(new Date());
        return dto;
    }



    // ======================== 工具方法 ========================

    /**
     * 将 Date 转为 Cron 表达式（7 字段：秒 分 时 日 月 周 年）。
     * <p>
     * 例如 2026-06-24 14:30:00 → "0 30 14 24 6 ? 2026"
     */
    public static String dateToCron(Date date) {
        if (date == null) return null;
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        return String.format("0 %d %d %d %d ? %d",
                cal.get(Calendar.MINUTE),
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.YEAR));
    }

}

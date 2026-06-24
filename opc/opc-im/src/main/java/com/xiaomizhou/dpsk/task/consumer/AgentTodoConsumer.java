package com.xiaomizhou.dpsk.task.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.xiaomizhou.dpsk.core.utils.WsUtils;
import com.xiaomizhou.dpsk.core.ws.SenderInfo;
import com.xiaomizhou.dpsk.core.ws.WsMessage;
import com.xiaomizhou.dpsk.core.ws.WsMsgType;
import com.xiaomizhou.dpsk.core.ws.payload.MessagePayload;
import com.xiaomizhou.dpsk.db.AgentComponent;
import com.xiaomizhou.dpsk.db.dao.TodoItemDao;
import com.xiaomizhou.dpsk.db.dto.AgentDto;
import com.xiaomizhou.dpsk.db.model.TodoItemDO;
import com.xiaomizhou.dpsk.task.model.Task;
import com.xiaomizhou.dpsk.task.model.TaskConsumeResult;
import com.xiaomizhou.dpsk.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 待办到期消费者 — 待办到期时通过 WebSocket 推送给前端。
 * <p>
 * 流程：
 * <ol>
 *   <li>从 triggerContext 获取 todo_code / agent_code / conversation_code</li>
 *   <li>查询待办详情（title、content、alarmSound 等）</li>
 *   <li>通过 WebSocket 推送 "todo_remind" 事件给前端</li>
 * </ol>
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/24
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AgentTodoConsumer implements TaskConsumer {

    public static final String CONSUMER_KEY = "AGENT_TODO";

    /** WS 消息类型：待办到期提醒 */
    public static final String TODO_REMIND = "todo_remind";

    private final TodoItemDao todoItemDao;
    private final AgentComponent agentComponent;

    @Override
    public String getConsumerKey() {
        return CONSUMER_KEY;
    }

    @Override
    public TaskConsumeResult consume(Task task, Map<String, Object> triggerContext) {
        String taskCode = task.getCode();
        log.info("AgentTodoConsumer triggered: task={}, triggerContext={}", taskCode, triggerContext);

        try {
            // 1. 从 triggerContext 获取参数
            String parameters = task.getParameters();
            Map params = JsonUtils.toObj(parameters, HashMap.class);
            String todoCode = MapUtils.getString(params, "todo_code");
            String agentCode = MapUtils.getString(params,"agent_code");
            String conversationCode = MapUtils.getString(params,"conversation_code");

            if (StringUtils.isAnyBlank(todoCode, agentCode)) {
                return TaskConsumeResult.fail("Missing required parameters: todo_code or agent_code");
            }

            // 2. 查询待办详情
            TodoItemDO todo = todoItemDao.getOne(
                    new LambdaQueryWrapper<TodoItemDO>()
                            .eq(TodoItemDO::getCode, todoCode)
                            .eq(TodoItemDO::getIsDeleted, 0));
            if (todo == null) {
                log.warn("AgentTodoConsumer: 待办不存在或已删除, todoCode={}", todoCode);
                return TaskConsumeResult.fail("Todo not found: " + todoCode);
            }

            // 3. 查询 agent 信息用于 sender
            AgentDto agent = agentComponent.getByCode(agentCode);
            SenderInfo senderInfo = new SenderInfo(
                    agentCode,
                    Objects.isNull(agent) ? "" : agent.getName(),
                    Objects.isNull(agent) ? "" : agent.getAvatar());

            // 4. 构建待办提醒 payload
            Map<String, Object> todoPayload = new LinkedHashMap<>();
            todoPayload.put("todoCode", todo.getCode());
            todoPayload.put("title", todo.getTitle());
            todoPayload.put("content", todo.getContent());
            todoPayload.put("alarmSound", todo.getAlarmSound());
            todoPayload.put("conversationCode", conversationCode);
            todoPayload.put("agentCode", agentCode);
            todoPayload.put("dueTime", todo.getDueTime() != null ? todo.getDueTime().getTime() : null);
            todoPayload.put("status", todo.getStatus());

            // 5. 通过 WebSocket 推送
            try {
                WsUtils.send(new WsMessage(WsMsgType.TODO_REMIND, todoPayload));
                log.info("AgentTodoConsumer: WS 推送待办提醒成功, todoCode={}, taskCode={}", todoCode, taskCode);
            } catch (Exception e) {
                log.warn("AgentTodoConsumer: WS 推送待办提醒失败, todoCode={}, taskCode={}", todoCode, taskCode, e);
                // WS 推送失败不影响任务执行结果
            }

            return TaskConsumeResult.ok("Todo remind sent, todoCode=" + todoCode);

        } catch (Exception e) {
            log.error("AgentTodoConsumer failed: task={}", taskCode, e);
            return TaskConsumeResult.fail(e.getMessage());
        }
    }
}

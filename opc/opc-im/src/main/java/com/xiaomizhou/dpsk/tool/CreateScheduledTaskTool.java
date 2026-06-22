package com.xiaomizhou.dpsk.tool;

import com.github.kagkarlsson.shaded.cronutils.parser.CronParser;
import com.xiaomizhou.dpsk.db.TaskComponent;
import com.xiaomizhou.dpsk.db.dao.ConversationDao;
import com.xiaomizhou.dpsk.db.dto.TaskCreateCmd;
import com.xiaomizhou.dpsk.db.model.Conversation;
import com.xiaomizhou.dpsk.task.TaskCreationContext;
import com.xiaomizhou.dpsk.task.consumer.AgentTaskConsumer;
import com.xiaomizhou.dpsk.task.consumer.NotificationTaskConsumer;
import com.xiaomizhou.dpsk.task.model.Task;
import com.xiaomizhou.dpsk.tool.model.ToolContext;
import com.xiaomizhou.dpsk.utils.JsonUtils;
import dev.langchain4j.agent.tool.P;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 可调用的定时任务创建工具。
 * <p>
 * Agent 在对话中调用此工具即可创建定时任务：
 * <ul>
 *   <li>NOTIFICATION — 纯提醒，到点直接发消息</li>
 *   <li>AGENT_TASK — AI 任务，到点触发 AI 流程执行</li>
 * </ul>
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/4
 */
@ToolMeta(value = "创建定时任务，可设置指定时间的纯提醒或让AI自动执行的AI任务",
        level = "normal",
        category = "任务",
        tags = {"定时任务", "提醒", "AI任务"})
@Slf4j
@RequiredArgsConstructor
public class CreateScheduledTaskTool {

    /** 默认上下文查询条数 */
    private static final int DEFAULT_CONTEXT_SIZE = 10;

    private final TaskComponent taskComponent;
    private final ConversationDao conversationDao;

    /**
     * 创建一个定时任务。
     * <p>
     * userId/agentCode/conversationCode 通过 {@link TaskCreationContext} 透传，
     * 不对外暴露给 LLM，避免大模型产生幻觉值。
     *
     * @param name           任务名称
     * @param cronExpression Cron 表达式，如 "0 0 9 * * ?" 表示每天 9 点
     * @param consumerKey    任务类型：NOTIFICATION（纯提醒）/ AGENT_TASK（AI执行）
     * @param content        任务内容（提醒文本 或 AI指令）
     * @return 创建结果
     */
    @dev.langchain4j.agent.tool.Tool(
            name = "create_scheduled_task",
            value = "创建定时任务")
    public String createScheduledTask(
            @P(name = "name", description = "任务名称，简要描述任务用途", required = true) String name,
            @P(name = "cronExpression", description = "Cron 表达式,例如：0 13 13 18 6 ? 表示6月18日13点13分", required = true) String cronExpression,
            @P(name = "consumerKey", description = "任务类型：NOTIFICATION=纯提醒，AGENT_TASK=AI执行", required = true) String consumerKey,
            @P(name = "content", description = "任务内容：提醒文本（NOTIFICATION时）或AI指令（AGENT_TASK时）", required = true) String content,
            @P(name = "toolContext", description = "工具上下文，不能传这个参数", required = false) ToolContext toolContext) {

        // 从 ThreadLocal 透传获取上下文参数
        String userId = toolContext.getUserCode();
        String conversationCode = toolContext.getConversationCode();
        String agentCode = toolContext.getAgentCode();

        try {
            // 参数校验
            if (StringUtils.isAnyBlank(name, cronExpression, consumerKey, content)) {
                return "创建任务失败：缺少必填参数（name, cronExpression, consumerKey, content）";
            }

            if (StringUtils.isBlank(userId) || StringUtils.isBlank(conversationCode)) {
                return "创建任务失败：缺少透传参数（userId 或 conversationCode），请检查上下文是否已初始化";
            }

            if (!NotificationTaskConsumer.CONSUMER_KEY.equals(consumerKey)
                    && !AgentTaskConsumer.CONSUMER_KEY.equals(consumerKey)) {
                return "创建任务失败：consumerKey 必须为 NOTIFICATION 或 AGENT_TASK，当前值: " + consumerKey;
            }

            if (AgentTaskConsumer.CONSUMER_KEY.equals(consumerKey) && StringUtils.isBlank(agentCode)) {
                return "创建任务失败：AGENT_TASK 类型必须指定 agentCode";
            }

            // 获取锚点消息编码（当前会话最新消息）
            String anchorMsgCode = getAnchorMsgCode(conversationCode);

            // 构建 parameters JSON
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("cron", cronExpression);
            params.put("userId", userId);

            if (AgentTaskConsumer.CONSUMER_KEY.equals(consumerKey)) {
                params.put("prompt", content);
                if (anchorMsgCode != null) {
                    params.put("anchorMsgCode", anchorMsgCode);
                    params.put("contextSize", DEFAULT_CONTEXT_SIZE);
                }
            } else {
                params.put("message", content);
            }

            String parameters = JsonUtils.toJson(params);

            // 创建任务
            TaskCreateCmd cmd = new TaskCreateCmd();
            cmd.setName(name);
            cmd.setTaskType(Task.TYPE_SCHEDULED);
            cmd.setConsumerKey(consumerKey);
            cmd.setParameters(parameters);
            cmd.setAgentCode(agentCode);
            cmd.setConversationCode(conversationCode);
            cmd.setSource(Task.SOURCE_AGENT);

            String taskCode = taskComponent.create(cmd).getCode();
            log.info("CreateScheduledTaskTool 创建任务成功: code={}, consumerKey={}, cron={}",
                    taskCode, consumerKey, cronExpression);

            String typeName = AgentTaskConsumer.CONSUMER_KEY.equals(consumerKey) ? "AI任务" : "提醒";
            return String.format("已成功创建%s「%s」，执行计划: %s，任务编码: %s",
                    typeName, name, cronExpression, taskCode);

        } catch (Exception e) {
            log.error("CreateScheduledTaskTool 创建任务失败", e);
            return "创建定时任务失败: " + e.getMessage();
        }
    }

    /**
     * 获取当前会话最新消息编码，作为锚点。
     */
    private String getAnchorMsgCode(String conversationCode) {
        try {
            Conversation conv = conversationDao.getOneByCode(conversationCode);
            return conv != null ? conv.getLastMessageCode() : null;
        } catch (Exception e) {
            log.warn("获取锚点消息编码失败: conversationCode={}", conversationCode, e);
            return null;
        }
    }
}

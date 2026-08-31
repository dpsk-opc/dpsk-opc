package com.xiaomizhou.dpsk.task.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaomizhou.dpsk.constant.ConversationType;
import com.xiaomizhou.dpsk.core.utils.WsUtils;
import com.xiaomizhou.dpsk.core.ws.SenderInfo;
import com.xiaomizhou.dpsk.core.ws.WsMessage;
import com.xiaomizhou.dpsk.core.ws.WsMsgType;
import com.xiaomizhou.dpsk.core.ws.payload.MessagePayload;
import com.xiaomizhou.dpsk.db.AgentComponent;
import com.xiaomizhou.dpsk.db.ChatMessageComponent;
import com.xiaomizhou.dpsk.db.dto.AgentDto;
import com.xiaomizhou.dpsk.db.dto.ChatMsgDto;
import com.xiaomizhou.dpsk.task.model.Task;
import com.xiaomizhou.dpsk.task.model.TaskConsumeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.Objects;

/**
 * 通知/提醒消费者 — 纯提醒，不执行 AI 流程。
 * <p>
 * 流程：
 * <ol>
 *   <li>解析 parameters 获取 userId、message</li>
 *   <li>创建 ChatMessage 入库（messageType = "AI"）</li>
 *   <li>通过 WebSocket 推送给用户</li>
 * </ol>
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/4
 */
@Slf4j
@RequiredArgsConstructor
public class NotificationTaskConsumer implements TaskConsumer {

    public static final String CONSUMER_KEY = "NOTIFICATION";

    private final ChatMessageComponent chatMessageComponent;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AgentComponent agentComponent;

    @Override
    public String getConsumerKey() {
        return CONSUMER_KEY;
    }

    @Override
    public TaskConsumeResult consume(Task task, Map<String, Object> triggerContext) {
        String taskCode = task.getCode();
        log.info("NotificationTaskConsumer triggered: task={}", taskCode);

        try {
            // 1. 解析 parameters
            String parameters = task.getParameters();
            if (StringUtils.isBlank(parameters)) {
                return TaskConsumeResult.fail("Task parameters is empty");
            }

            JsonNode params = objectMapper.readTree(parameters);
            String userId = getString(params, "userId");
            String message = getString(params, "message");

            if (StringUtils.isAnyBlank(userId, message)) {
                return TaskConsumeResult.fail("Missing required parameters: userId or message");
            }

            String agentCode = StringUtils.defaultIfBlank(task.getAgentCode(), userId);
            String conversationCode = task.getConversationCode();

            // 2. 创建通知消息入库
            String notificationContent = "[提醒: " + task.getName() + "]\n" + message;

            String msgCode = chatMessageComponent.newSingleChatMsg(agentCode,
                    ChatMsgDto.builder()
                            .targetId(userId)
                            .sendId(agentCode)
                            .message(notificationContent)
                            .messageType("SYSTEM")
                            .conversationType(ConversationType.SINGLE.getCode())
                            .conversationCode(conversationCode)
                            .taskId(taskCode)
                            .status("IGNORE")
                            .build(),
                    null, null);

            if (StringUtils.isBlank(msgCode)) {
                return TaskConsumeResult.fail("Failed to create notification message");
            }

            log.info("NotificationTaskConsumer created message: msgCode={}, task={}", msgCode, taskCode);

            AgentDto agent = agentComponent.getByCode(agentCode);

            // 3. WebSocket 推送
            try {
                SenderInfo senderInfo = new SenderInfo(agentCode, Objects.isNull(agent) ? "" : agent.getName(), Objects.isNull(agent) ? "" : agent.getAvatar());
                WsUtils.send(new WsMessage(WsMsgType.MESSAGE,
                        new MessagePayload(
                                msgCode,                 // messageId
                                conversationCode,        // conversationId
                                taskCode,                // taskId
                                "text",                  // messageType
                                notificationContent,     // content
                                senderInfo,              // sender
                                System.currentTimeMillis(), // timestamp
                                null,                    // replyToId
                                Map.of()                 // metadata
                        )));
            } catch (Exception e) {
                log.warn("Failed to push notification via WebSocket: msgCode={}", msgCode, e);
                // WebSocket 推送失败不影响任务执行结果
            }

            return TaskConsumeResult.ok("Notification sent, msgCode=" + msgCode);

        } catch (Exception e) {
            log.error("NotificationTaskConsumer failed: task={}", taskCode, e);
            return TaskConsumeResult.fail(e.getMessage());
        }
    }

    private String getString(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }
}

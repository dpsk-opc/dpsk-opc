package com.xiaomizhou.dpsk.task.consumer;

import com.xiaomizhou.dpsk.agent.AgentOrchestrator;
import com.xiaomizhou.dpsk.agent.data.AgentDefProvider;
import com.xiaomizhou.dpsk.db.AgentComponent;
import com.xiaomizhou.dpsk.db.ChatMessageComponent;
import com.xiaomizhou.dpsk.db.dao.ConversationDao;
import com.xiaomizhou.dpsk.db.dao.TokenUsageDao;
import com.xiaomizhou.dpsk.memory.repository.MessageRepository;
import com.xiaomizhou.dpsk.db.TaskComponent;
import com.xiaomizhou.dpsk.tool.CreateScheduledTaskTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import java.util.Map;

/**
 * 定时任务消费者 Spring 配置。
 * <p>
 * 注册 NOTIFICATION 和 AGENT_TASK 两个消费者到 TaskConsumerRegistry，
 * 以及创建 CreateScheduledTaskTool 工具 Bean。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/4
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class TaskConsumerConfiguration {

    private final TaskConsumerRegistry taskConsumerRegistry;

    // ======================== 消费者 Bean ========================

    @Bean
    public NotificationTaskConsumer notificationTaskConsumer(ChatMessageComponent chatMessageComponent, AgentComponent agentComponent) {
        log.info("Creating NotificationTaskConsumer bean");
        return new NotificationTaskConsumer(chatMessageComponent, agentComponent);
    }

    @Bean
    public AgentTaskConsumer agentTaskConsumer(AgentOrchestrator orchestrator,
                                                MessageRepository messageRepository,
                                                ChatMessageComponent chatMessageComponent,
                                                AgentComponent agentComponent,
                                                AgentDefProvider agentDefProvider,
                                                TokenUsageDao tokenUsageDao) {
        log.info("Creating AgentTaskConsumer bean");
        return new AgentTaskConsumer(orchestrator, messageRepository,
                chatMessageComponent, agentComponent, agentDefProvider, tokenUsageDao);
    }

    // ======================== 工具 Bean ========================

    @Bean
    public CreateScheduledTaskTool createScheduledTaskTool(TaskComponent taskComponent,
                                                            ConversationDao conversationDao) {
        log.info("Creating CreateScheduledTaskTool bean");
        return new CreateScheduledTaskTool(taskComponent, conversationDao);
    }

    // ======================== 注册消费者 ========================

    @EventListener(ApplicationReadyEvent.class)
    public void registerConsumers(ApplicationReadyEvent event) {
        var ctx = event.getApplicationContext();

        Map<String, TaskConsumer> consumers = ctx.getBeansOfType(TaskConsumer.class);

        consumers.values().forEach(taskConsumerRegistry::register);
    }
}

package com.xiaomizhou.dpsk.config;

import com.xiaomizhou.dpsk.task.TaskManager;
import com.xiaomizhou.dpsk.task.TaskManagerImpl;
import com.xiaomizhou.dpsk.task.consumer.KnowLedgeBuildTaskConsumer;
import com.xiaomizhou.dpsk.task.model.Task;
import com.xiaomizhou.dpsk.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class BuildInSchedulerConfig implements InitializingBean {

    private final TaskManager taskManager;

    private final KnowLedgeBuildTaskConsumer knowLedgeBuildTaskConsumer;

    private static final String KNOWLEDGE_BUILD_TASK_CODE = "KNOWLEDGE_BUILD_TASK";

    @Value("${com.xiaomizhou.dpsk.opc.knowledge.build.cron:0 3/5 * * * ?}")
    private String cron;

    private synchronized void initKnowledgeBuildTask() {

        String name = "知识库构建任务";
        Task task = taskManager.getTask(KNOWLEDGE_BUILD_TASK_CODE);

        Map<String, Object> params = new HashMap<>();
        params.put("cron", cron);
        if (Objects.isNull(task)) {

            Task tk = new Task();
            tk.setCode(KNOWLEDGE_BUILD_TASK_CODE);
            tk.setName(name);
            tk.setConsumerKey(knowLedgeBuildTaskConsumer.getConsumerKey());
            tk.setStatus(Task.STATUS_ENABLED);
            tk.setTaskType(Task.TYPE_SCHEDULED);
            tk.setParameters(JsonUtils.toJson(params));

            taskManager.createTask(tk);

            log.info("创建知识库构建任务成功, code={},cron:{}", tk.getCode(), cron);
            return;
        }

        Map<String, Object> oldParams = JsonUtils.toObj(task.getParameters(), HashMap.class);
        if (Objects.isNull(oldParams)) {
            return;
        }

        String oldCron = MapUtils.getString(oldParams, "cron");
        if (Strings.CS.equals(oldCron, cron)) {
            return;
        }


        Task tk = new Task();
        tk.setCode(KNOWLEDGE_BUILD_TASK_CODE);
        tk.setParameters(JsonUtils.toJson(params));
        taskManager.updateTask(tk);
        log.info("更新知识库构建任务成功, code={},cron:{}", tk.getCode(), cron);
    }

    @Override
    public void afterPropertiesSet() throws Exception {

        try {
            initKnowledgeBuildTask();
        } catch (Exception e) {
            log.error("内置任务初始化失败", e);
        }
    }
}

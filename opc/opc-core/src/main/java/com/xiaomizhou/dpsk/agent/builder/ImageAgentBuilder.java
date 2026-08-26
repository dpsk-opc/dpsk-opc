package com.xiaomizhou.dpsk.agent.builder;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.xiaomizhou.dpsk.agent.AgentBuildSpec;
import com.xiaomizhou.dpsk.agent.AgentBuilder;
import com.xiaomizhou.dpsk.agent.AgentPipeline;
import com.xiaomizhou.dpsk.agent.PipelineResult;
import com.xiaomizhou.dpsk.agent.data.AgentDef;
import com.xiaomizhou.dpsk.agent.data.AgentDefProvider;
import com.xiaomizhou.dpsk.agent.event.AgentEvent;
import com.xiaomizhou.dpsk.agent.event.AgentEventType;
import com.xiaomizhou.dpsk.agent.factory.AgentComponentFactory;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.openai.OpenAiImageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
public class ImageAgentBuilder implements AgentBuilder {

    private final AgentDefProvider agentDefProvider;

    private final AgentComponentFactory factory;

    @Override
    public String supportedMode() {
        return AgentBuildSpec.MODE_IMAGE;
    }

    @Override
    public AgentPipeline build(AgentBuildSpec spec) {

        String targetAgentCode = spec.getTargetAgentCode();

        // 1. 查询 Agent 定义
        AgentDef agentDef = agentDefProvider.getByCode(targetAgentCode);
        if (agentDef == null) {
            throw new IllegalArgumentException("Agent not found: " + targetAgentCode);
        }

        AgentBuildSpec.ImageBuildSpec imageSpec = spec.getImageBuildSpec();
        if (Objects.isNull(imageSpec)) {
            throw new IllegalArgumentException("ImageBuildSpec is null");
        }

        OpenAiImageModel model = factory.createImageModel(agentDef.getLlmConfig(), imageSpec);
        if (Objects.isNull(model)) {
            throw new IllegalArgumentException("OpenAiImageModel is null");
        }


        return callback -> {


            Response<List<Image>> response = model.generate(spec.getUserContent(), imageSpec.getN());

            TokenUsage usage = response.tokenUsage();
            List<Image> images = response.content();

            if (CollectionUtils.isNotEmpty(images)) {

                String url = Joiner.on("\n").join(images.stream().map(image -> {
                    return """
                            ![生成图片](%s)
                            """.formatted(image.url());
                }).toList());

                // 发送 MESSAGE 事件
                callback.onEvent(new AgentEvent(AgentEventType.MESSAGE, agentDef.getCode(), null, null, null, null, null));

                // 发送第一个字符
                callback.onEvent(new AgentEvent(AgentEventType.MESSAGE_CHUNK, agentDef.getCode(), url, null, null, null, null));

                // 发送结束事件
                callback.onEvent(new AgentEvent(AgentEventType.MESSAGE_CHUNK_END, agentDef.getCode(), null, null, null, null, null));


                Map<String, Object> meta = new HashMap<>();
                meta.put("content", url);
//                meta.put("contentType","")
                if (usage != null) {
                    meta.put("tokenUsage", usage);
                }
                callback.onEvent(AgentEvent.done(agentDef.getCode(), meta));
            }

            callback.onComplete();
            return PipelineResult.builder()
                    .success(true)
                    .outputText("生成成功")
                    .build();
        };
    }
}

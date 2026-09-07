package com.xiaomizhou.dpsk.agent.data;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AgentDef {

    private String accessKey;

    private String baseUrl;

    private String modelName;

    private String temperature;


    private Integer maxTokens;

    private Integer topP;

    private Double frequencyPenalty;

    private Double presencePenalty;

    private List<String> stop;

    private String code;

    private String name;

    private String llmConfig;

    private String nickname;

    private String avatar;

    private String mbti;

    private String prompt;

    private String role;

    private String workspace;

    private Integer sex;

    private String slogan;

    /**
     * 能力标签列表（由 LLM 从 prompt 提取，供群聊 Picker 做能力匹配）。
     */
    private List<String> capabilities;

    public String toPersonaText() {
        return """
                Your name:%s
                Your nickname:%s
                Your mbti:%s
                Your sex:%s
                Your role:%s
                Your workspace:%s
                Your slogan:%s
                Your prompt:%s
                """.formatted(name, nickname, mbti, 1 == sex ? "男" : "女", role, workspace, slogan, prompt);
    }

}

package com.xiaomizhou.dpsk.db;

import com.xiaomizhou.dpsk.agent.data.AgentDef;
import com.xiaomizhou.dpsk.agent.data.AgentDefProvider;
import com.xiaomizhou.dpsk.db.dto.AgentDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * ImAgentDefProvider — opc-im 层对 AgentDefProvider 接口的实现。
 * 基于 AgentComponent 从 t_agent 表查询 Agent 定义，并转换为 opc-core 的 AgentDef 模型。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/3
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ImAgentDefProvider implements AgentDefProvider {

    private final AgentComponent agentComponent;

    @Override
    public AgentDef getByCode(String code) {
        AgentDto dto = agentComponent.getByCode(code);
        return convertToAgentDef(dto);
    }

    @Override
    public List<AgentDef> getByCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Collections.emptyList();
        }
        List<AgentDto> dtos = agentComponent.getByCodes(codes);
        if (dtos == null || dtos.isEmpty()) {
            return Collections.emptyList();
        }
        return dtos.stream()
                .map(this::convertToAgentDef)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 将 opc-im 的 AgentDto 转换为 opc-core 的 AgentDef。
     */
    private AgentDef convertToAgentDef(AgentDto dto) {
        if (dto == null) {
            return null;
        }
        return AgentDef.builder()
                .code(dto.getCode())
                .name(dto.getName())
                .nickname(dto.getNickname())
                .avatar(dto.getAvatar())
                .mbti(dto.getMbti())
                .prompt(dto.getPrompt())
                .role(dto.getRole())
                .workspace(dto.getWorkspace())
                .sex(dto.getSex())
                .slogan(dto.getSlogan())
                .llmConfig(dto.getLlmConfig())
                .capabilities(dto.getCapabilities() == null ? Collections.emptyList() : dto.getCapabilities())
                .build();
    }
}

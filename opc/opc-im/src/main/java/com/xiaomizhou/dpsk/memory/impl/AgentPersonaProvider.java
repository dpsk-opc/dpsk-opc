package com.xiaomizhou.dpsk.memory.impl;

import com.xiaomizhou.dpsk.db.AgentComponent;
import com.xiaomizhou.dpsk.db.dto.AgentDto;
import com.xiaomizhou.dpsk.memory.PersonaProvider;
import com.xiaomizhou.dpsk.utils.AgentUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PersonaProvider 的 opc-im 实现，通过 AgentComponent 查询 Agent 的人设。
 * <p>
 * 与旧版 DefaultMemory（第 48-51 行）的行为一致：
 * 通过 AgentUtils.toAgentDef() 将 AgentDto 转为 persona 文本。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/30
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AgentPersonaProvider implements PersonaProvider {

    private final AgentComponent agentComponent;

    @Override
    public String getPersona(String agentCode) {
        AgentDto agent = agentComponent.getByCode(agentCode);
        if (agent == null) {
            log.warn("Agent not found for persona: {}", agentCode);
            return null;
        }
        return AgentUtils.toAgentDef(agent);
    }
}

package com.xiaomizhou.dpsk.db.dao;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaomizhou.dpsk.db.mapper.AgentToolRefMapper;
import com.xiaomizhou.dpsk.db.model.AgentToolRefDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * Agent工具绑定 DAO。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/1
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AgentToolRefDao extends ServiceImpl<AgentToolRefMapper, AgentToolRefDO> {

    /**
     * 根据 agent_code 查询绑定的工具编码列表。
     */
    public List<AgentToolRefDO> findByAgentCode(String agentCode) {
        return lambdaQuery()
                .eq(AgentToolRefDO::getAgentCode, agentCode)
                .eq(AgentToolRefDO::getIsDeleted, 0)
                .list();
    }


    /**
     * 删除指定 agent 的所有绑定（逻辑删除）。
     */
    public boolean removeByAgentCode(String agentCode) {
        if (StringUtils.isBlank(agentCode)) {
            return false;
        }
        return baseMapper.hardDelByCode(agentCode);
    }

    public boolean unbindTools(String agentCode, List<String> toolCodes) {

        if (StringUtils.isBlank(agentCode) || CollectionUtils.isEmpty(toolCodes)) {
            return false;
        }

        toolCodes.forEach(toolCode -> {
            if (StringUtils.isBlank(toolCode)) {
                return;
            }
            baseMapper.hardDelByCodeAndToolCode(agentCode, toolCode);
        });

        return true;
    }

    /**
     * 批量保存绑定关系。
     */
    public boolean saveBatch(List<AgentToolRefDO> refs) {
        return super.saveBatch(refs);
    }

    /**
     * 根据 agent_code 和 tool_code 检查绑定是否存在。
     */
    public boolean existsByAgentAndTool(String agentCode, String toolCode) {
        return lambdaQuery()
                .eq(AgentToolRefDO::getAgentCode, agentCode)
                .eq(AgentToolRefDO::getToolCode, toolCode)
                .eq(AgentToolRefDO::getIsDeleted, 0)
                .count() > 0;
    }
}

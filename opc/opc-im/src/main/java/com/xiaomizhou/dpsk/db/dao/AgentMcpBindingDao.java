package com.xiaomizhou.dpsk.db.dao;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.collect.Maps;
import com.xiaomizhou.dpsk.db.mapper.AgentMcpBindingMapper;
import com.xiaomizhou.dpsk.db.model.AgentMcpBindingDO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent MCP 绑定 DAO。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/16
 */
@Component
@Slf4j
public class AgentMcpBindingDao extends ServiceImpl<AgentMcpBindingMapper, AgentMcpBindingDO> {

    public List<AgentMcpBindingDO> findByAgentCode(String agentCode) {

        if(StringUtils.isBlank(agentCode)){
            return Collections.emptyList();
        }

        return lambdaQuery()
                .eq(AgentMcpBindingDO::getAgentCode, agentCode)
                .eq(AgentMcpBindingDO::getIsDeleted, 0)
                .list();
    }

    public Map<String, List<AgentMcpBindingDO>> findByAgentCodes(List<String> agentCodes) {

        if (CollectionUtils.isEmpty(agentCodes)) {
            return Collections.emptyMap();
        }

        List<AgentMcpBindingDO> list = lambdaQuery()
                .in(AgentMcpBindingDO::getAgentCode, agentCodes)
                .list();

        return list.stream().collect(Collectors.groupingBy(AgentMcpBindingDO::getAgentCode));
    }


    public AgentMcpBindingDO findByAgentAndTemplate(String agentCode, String templateCode) {
        return lambdaQuery()
                .eq(AgentMcpBindingDO::getAgentCode, agentCode)
                .eq(AgentMcpBindingDO::getTemplateCode, templateCode)
                .eq(AgentMcpBindingDO::getIsDeleted, 0)
                .one();
    }

    public AgentMcpBindingDO getByCode(String code) {
        return lambdaQuery()
                .eq(AgentMcpBindingDO::getCode, code)
                .eq(AgentMcpBindingDO::getIsDeleted, 0)
                .one();
    }

    public List<AgentMcpBindingDO> findByTemplateCode(String templateCode) {
        return lambdaQuery()
                .eq(AgentMcpBindingDO::getTemplateCode, templateCode)
                .eq(AgentMcpBindingDO::getIsDeleted, 0)
                .list();
    }

    public boolean deleteByAgentAndTemplate(String agentCode, String templateCode) {
        return lambdaUpdate()
                .eq(AgentMcpBindingDO::getAgentCode, agentCode)
                .eq(AgentMcpBindingDO::getTemplateCode, templateCode)
                .set(AgentMcpBindingDO::getIsDeleted, 1)
                .update();
    }

    /**
     * 物理删除（绕过 @TableLogic），用于删除绑定时彻底清理。
     */
    public boolean hardDeleteById(Long id) {
        return baseMapper.deleteById(id) > 0;
    }

    public List<AgentMcpBindingDO> findEnabledByAgentCode(String agentCode) {
        return lambdaQuery()
                .eq(AgentMcpBindingDO::getAgentCode, agentCode)
                .eq(AgentMcpBindingDO::getEnabled, 1)
                .eq(AgentMcpBindingDO::getIsDeleted, 0)
                .list();
    }
}

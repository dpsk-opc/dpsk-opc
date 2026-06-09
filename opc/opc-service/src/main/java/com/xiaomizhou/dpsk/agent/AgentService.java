//package com.xiaomizhou.dpsk.agent;
//
//import com.xiaomizhou.dpsk.agent.dto.AgentDef;
//import com.xiaomizhou.dpsk.agent.service.AgentRegistry;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.apache.commons.collections.CollectionUtils;
//import org.apache.commons.lang3.StringUtils;
//import org.apache.commons.lang3.tuple.ImmutablePair;
//import org.springframework.stereotype.Component;
//
//import java.util.List;
//import java.util.Objects;
//
///**
// * @author eason - vipzhsh@163.com
// * @date 2026/5/14 9:13
// * @description
// */
//@RequiredArgsConstructor
//@Slf4j
//@Component
//public class AgentService {
//
//    private final AgentRegistry agentRegistry;
//
//
//    /**
//     * 获取Agent
//     *
//     * @param agentId
//     * @return
//     */
//    public AgentDef getOneByAgentId(String agentId) {
//
//        if (StringUtils.isBlank(agentId)) {
//            return null;
//        }
//
//        return agentRegistry.getAgentById(agentId);
//    }
//
//    /**
//     * 获取Agent列表
//     *
//     * @param pageNo
//     * @param pageSize
//     * @param name
//     * @return
//     */
//    public ImmutablePair<Long, List<AgentDef>> getAgents(int pageNo, int pageSize, String name) {
//        ImmutablePair<Long, List<AgentDef>> agents = agentRegistry.getAgents(pageNo, pageSize, name);
//        if (Objects.isNull(agents) || CollectionUtils.isEmpty(agents.right)) {
//            return ImmutablePair.of(0L, List.of());
//        }
//
//        agents.right.sort((o1, o2) -> o2.getName().compareTo(o1.getName()));
//        return agents;
//    }
//
//
//}

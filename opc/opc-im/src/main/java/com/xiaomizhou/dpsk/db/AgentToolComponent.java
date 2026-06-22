package com.xiaomizhou.dpsk.db;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.xiaomizhou.dpsk.db.dao.AgentDao;
import com.xiaomizhou.dpsk.db.dao.AgentMcpBindingDao;
import com.xiaomizhou.dpsk.db.dao.AgentToolRefDao;
import com.xiaomizhou.dpsk.db.dao.ToolDao;
import com.xiaomizhou.dpsk.db.dto.AgentToolBindCmd;
import com.xiaomizhou.dpsk.db.dto.AgentToolRefVO;
import com.xiaomizhou.dpsk.db.model.Agent;
import com.xiaomizhou.dpsk.db.model.AgentMcpBindingDO;
import com.xiaomizhou.dpsk.db.model.AgentToolRefDO;
import com.xiaomizhou.dpsk.db.model.ToolDO;
import com.xiaomizhou.dpsk.core.exceptions.BusinessException;
import com.xiaomizhou.dpsk.tool.SourceType;
import com.xiaomizhou.dpsk.tool.ToolRegistry;
import com.xiaomizhou.dpsk.tool.model.ToolMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Agent 工具绑定业务组件。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/1
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AgentToolComponent {

    private final AgentToolRefDao agentToolRefDao;
    private final ToolDao toolDao;
    private final AgentDao agentDao;

    private final ToolRegistry toolRegistry;

    /**
     * 查询 Agent 绑定的工具列表。
     * 若 agentCode 为空，则查询所有在用的工具（有绑定记录的工具列表，去重）。
     *
     * @param agentCode Agent 编码，可为空
     * @return 工具绑定 VO 列表
     */
    public List<AgentToolRefVO> queryByAgentCode(String agentCode) {


        if (StringUtils.isBlank(agentCode)) {
            return toolDao.list(Wrappers.<ToolDO>lambdaQuery().eq(ToolDO::getSourceType, SourceType.LOCAL).eq(ToolDO::getStatus, "ENABLED")).stream().map(t -> {
                AgentToolRefVO vo = new AgentToolRefVO();

                vo.setAgentCode(agentCode);
                vo.setToolCode(t.getCode());
                vo.setToolCategory(t.getCategory());
                vo.setToolDescription(t.getDescription());
                vo.setToolName(t.getName());
                return vo;
            }).toList();
        }

        List<ToolMetadata> tools = toolRegistry.getToolsForAgent(agentCode);

        return tools.stream().filter(t -> SourceType.LOCAL.equalsIgnoreCase(t.getSourceType())).map(t -> {
            AgentToolRefVO vo = new AgentToolRefVO();

            vo.setAgentCode(agentCode);
            vo.setToolCode(t.getCode());
            vo.setToolCategory(t.getCategory());
            vo.setToolDescription(t.getDescription());
            vo.setToolName(t.getName());
            return vo;
        }).toList();
    }

    public Map<String, List<AgentToolRefVO>> queryByAgentCodes(List<String> agentCodes) {

        if (CollectionUtils.isEmpty(agentCodes)) {
            return Map.of();
        }

        Map<String, List<AgentToolRefVO>> result = Maps.newHashMap();

        agentCodes.forEach(agentCode -> {
            result.put(agentCode, queryByAgentCode(agentCode));
        });

        return result;
    }

    /**
     * 修改 Agent 与工具的绑定关系（全量替换）。
     * 1. 校验 agent 是否存在
     * 2. 逻辑删除该 agent 的所有旧绑定
     * 3. 批量插入新的绑定关系
     *
     * @param cmd 绑定命令
     */
    @Transactional(rollbackFor = Exception.class)
    public void bindTools(AgentToolBindCmd cmd) {
        if (StringUtils.isBlank(cmd.getAgentCode())) {
            throw BusinessException.paramError("agentCode 不能为空");
        }

        // 校验 agent 是否存在
        Agent agent = agentDao.getByCode(cmd.getAgentCode());
        if (agent == null) {
            throw BusinessException.notFound("Agent 不存在: " + cmd.getAgentCode());
        }

//        // 逻辑删除现有绑定
//        agentToolRefDao.removeByAgentCode(cmd.getAgentCode());

        // 如果 toolCodes 为空，则仅解绑，不新增
        if (CollectionUtils.isEmpty(cmd.getToolCodes())) {
            log.info("清空 Agent[{}] 的所有工具绑定", cmd.getAgentCode());
            return;
        }

        // 去重
        List<String> toolCodes = cmd.getToolCodes().stream()
                .filter(StringUtils::isNotBlank)
                .distinct()
                .collect(Collectors.toList());

        if (toolCodes.isEmpty()) {
            return;
        }

        // 校验 tool 是否存在
        List<ToolDO> tools = toolDao.lambdaQuery()
                .in(ToolDO::getCode, toolCodes)
                .eq(ToolDO::getIsDeleted, 0)
                .list();
        Set<String> existingToolCodes = tools.stream()
                .map(ToolDO::getCode)
                .collect(Collectors.toSet());

        List<String> invalidCodes = toolCodes.stream()
                .filter(tc -> !existingToolCodes.contains(tc))
                .collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(invalidCodes)) {
            throw BusinessException.paramError("工具编码不存在: " + String.join(", ", invalidCodes));
        }

        // 批量插入新绑定
        Date now = new Date();
        List<AgentToolRefDO> newRefs = toolCodes.stream().map(toolCode -> {
            AgentToolRefDO ref = new AgentToolRefDO();
            ref.setAgentCode(cmd.getAgentCode());
            ref.setToolCode(toolCode);
            ref.setCreateTime(now);
            ref.setUpdateTime(now);
            ref.setIsDeleted(0);
            return ref;
        }).collect(Collectors.toList());

        agentToolRefDao.saveBatch(newRefs);
        log.info("Agent[{}] 绑定工具成功, 绑定数量={}", cmd.getAgentCode(), newRefs.size());
    }

    /**
//     * 将 DO 列表转为 VO 列表，附带工具名称和描述。
//     */
//    private List<AgentToolRefVO> toVOList(List<AgentToolRefDO> refs) {
//        if (CollectionUtils.isEmpty(refs)) {
//            return List.of();
//        }
//
//        // 收集所有 tool_code，批量查询工具信息
//        Set<String> toolCodes = refs.stream()
//                .map(AgentToolRefDO::getToolCode)
//                .collect(Collectors.toSet());
//
//        Map<String, ToolDO> toolMap = toolDao.lambdaQuery()
//                .in(ToolDO::getCode, toolCodes)
//                .eq(ToolDO::getIsDeleted, 0)
//                .list()
//                .stream()
//                .collect(Collectors.toMap(ToolDO::getCode, Function.identity(), (a, b) -> a));
//
//        return refs.stream()
//                .map(ref -> {
//                    ToolDO tool = toolMap.get(ref.getToolCode());
//                    return new AgentToolRefVO(
//                            ref.getAgentCode(),
//                            ref.getToolCode(),
//                            tool != null ? tool.getName() : "",
//                            tool != null ? tool.getDescription() : ""
//                    );
//                })
//                .collect(Collectors.toList());
//    }

    public void unbindTools(AgentToolBindCmd cmd) {

        if (CollectionUtils.isEmpty(cmd.getToolCodes())) {
            return;
        }

        List<String> toolCodes = cmd.getToolCodes().stream()
                .filter(StringUtils::isNotBlank)
                .distinct()
                .collect(Collectors.toList());

        if (CollectionUtils.isEmpty(toolCodes)) {
            return;
        }

        agentToolRefDao.unbindTools(cmd.getAgentCode(), cmd.getToolCodes());
    }
}

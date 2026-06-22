package com.xiaomizhou.dpsk.tool;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.google.common.collect.Lists;
import com.xiaomizhou.dpsk.db.AgentToolComponent;
import com.xiaomizhou.dpsk.db.dao.AgentMcpBindingDao;
import com.xiaomizhou.dpsk.db.dao.AgentToolRefDao;
import com.xiaomizhou.dpsk.db.dao.ToolDao;
import com.xiaomizhou.dpsk.db.dto.AgentToolRefVO;
import com.xiaomizhou.dpsk.db.model.AgentMcpBindingDO;
import com.xiaomizhou.dpsk.db.model.AgentToolRefDO;
import com.xiaomizhou.dpsk.db.model.ToolDO;
import com.xiaomizhou.dpsk.tool.model.ToolMetadata;
import com.xiaomizhou.dpsk.tool.repository.ToolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.springframework.stereotype.Component;

import javax.xml.transform.Source;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * ToolRepository 实现，基于 MyBatis-Plus。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/30
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ToolRepositoryImpl implements ToolRepository {

    private final ToolDao toolDao;

    private final AgentMcpBindingDao agentMcpBindingDao;

    private final AgentToolRefDao agentToolRefDao;

    @Override
    public List<ToolMetadata> findAllEnabled() {
        return toolDao.findAllEnabled().stream()
                .map(this::toCoreModel)
                .collect(Collectors.toList());
    }

    @Override
    public ToolMetadata findByName(String name) {
        ToolDO entity = toolDao.getByName(name);
        return toCoreModel(entity);
    }

    @Override
    public ToolMetadata findByCode(String code) {
        ToolDO entity = toolDao.getByCode(code);
        return toCoreModel(entity);
    }

    @Override
    public List<ToolMetadata> findBySourceType(String sourceType) {
        return toolDao.findBySourceType(sourceType).stream()
                .map(this::toCoreModel)
                .collect(Collectors.toList());
    }

    @Override
    public List<ToolMetadata> findByCategory(String category) {
        return toolDao.findByCategory(category).stream()
                .map(this::toCoreModel)
                .collect(Collectors.toList());
    }

    @Override
    public List<ToolMetadata> findByOwnerAgent(String ownerAgentCode) {

        // 本地工具

        List<AgentToolRefDO> agentTools = agentToolRefDao.findByAgentCode(ownerAgentCode);

        List<ToolDO> result = Lists.newArrayList();
        if (CollectionUtils.isNotEmpty(agentTools)) {

            var toolCodes = agentTools.stream().map(AgentToolRefDO::getToolCode).collect(Collectors.toSet());

            if (CollectionUtils.isNotEmpty(toolCodes)) {
                result.addAll(toolDao.lambdaQuery().in(ToolDO::getCode, toolCodes)
                        .eq(ToolDO::getSourceType, SourceType.LOCAL)
                        .eq(ToolDO::getStatus, "ENABLED")
                        .list());
            }
        }

        // mcp工具
        List<ToolDO> mcps = toolDao.list(Wrappers.<ToolDO>lambdaQuery().eq(ToolDO::getOwnerAgentCode, ownerAgentCode)
                .eq(ToolDO::getSourceType, SourceType.MCP)
                .eq(ToolDO::getStatus, "ENABLED"));

        if (CollectionUtils.isNotEmpty(mcps)) {
            result.addAll(mcps);
        }

        // build-in
        List<ToolDO> buildInTools = toolDao.findByCategory(ToolCategory.BUILD_IN);
        if (CollectionUtils.isNotEmpty(buildInTools)) {
            result.addAll(buildInTools);
        }

        return result.stream().map(this::toCoreModel).distinct().collect(Collectors.toList());
    }

    @Override
    public List<ToolMetadata> searchByKeyword(String keyword) {
        return toolDao.searchByKeyword(keyword).stream()
                .map(this::toCoreModel)
                .collect(Collectors.toList());
    }

    @Override
    public void save(ToolMetadata metadata) {
        ToolDO entity = toDbModel(metadata);
        if (metadata.getId() != null) {
            toolDao.updateById(entity);
        } else {
            // 检查是否已存在
            ToolDO existing = toolDao.getByCode(metadata.getCode());
            if (existing != null) {
                entity.setId(existing.getId());
                toolDao.updateById(entity);
            } else {
                toolDao.save(entity);
            }
        }
        log.debug("Saved tool: code={}, name={}", metadata.getCode(), metadata.getName());
    }

    @Override
    public void saveBatch(List<ToolMetadata> metadataList) {
        List<ToolDO> entities = metadataList.stream()
                .map(this::toDbModel)
                .collect(Collectors.toList());
        toolDao.saveBatch(entities);
        log.debug("Batch saved {} tools", entities.size());
    }

    @Override
    public void updateStatus(String code, String status) {
        toolDao.updateStatus(code, status);
        log.debug("Updated tool status: code={}, status={}", code, status);
    }

    @Override
    public void deleteByCode(String code) {
        toolDao.deleteByCode(code);
        log.debug("Deleted tool: code={}", code);
    }

    @Override
    public boolean existsByCode(String code) {
        return toolDao.existsByCode(code);
    }

    // ---- 模型转换 ----

    private ToolMetadata toCoreModel(ToolDO entity) {
        if (entity == null) {
            return null;
        }
        return ToolMetadata.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .name(entity.getName())
                .description(entity.getDescription())
                .parametersSchema(entity.getParametersSchema())
                .sourceType(entity.getSourceType())
                .sourceRef(entity.getSourceRef())
                .riskLevel(entity.getRiskLevel())
                .status(entity.getStatus())
                .category(entity.getCategory())
                .tags(entity.getTags())
                .cacheable(entity.getCacheable() != null && entity.getCacheable() == 1)
                .timeoutMs(entity.getTimeoutMs())
                .build();
    }

    private ToolDO toDbModel(ToolMetadata model) {
        ToolDO entity = new ToolDO();
        entity.setId(model.getId());
        entity.setCode(model.getCode());
        entity.setName(model.getName());
        entity.setDescription(model.getDescription());
        entity.setParametersSchema(model.getParametersSchema());
        entity.setSourceType(model.getSourceType());
        entity.setSourceRef(model.getSourceRef());
        entity.setRiskLevel(model.getRiskLevel());
        entity.setStatus(model.getStatus());
        entity.setCategory(model.getCategory());
        entity.setTags(model.getTags());
        entity.setCacheable(model.getCacheable() != null && model.getCacheable() ? 1 : 0);
        entity.setTimeoutMs(model.getTimeoutMs());
        entity.setCreateTime(new Date());
        entity.setUpdateTime(new Date());
        entity.setIsDeleted(0);
        return entity;
    }
}

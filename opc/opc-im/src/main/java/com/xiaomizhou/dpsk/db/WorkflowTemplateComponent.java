package com.xiaomizhou.dpsk.db;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.xiaomizhou.dpsk.core.exceptions.BusinessException;
import com.xiaomizhou.dpsk.db.dao.WorkflowTemplateDao;
import com.xiaomizhou.dpsk.db.dto.WorkflowTemplateCreateCmd;
import com.xiaomizhou.dpsk.db.dto.WorkflowTemplateDto;
import com.xiaomizhou.dpsk.db.dto.WorkflowTemplateQueryParam;
import com.xiaomizhou.dpsk.db.dto.WorkflowTemplateUpdateCmd;
import com.xiaomizhou.dpsk.db.model.WorkflowTemplateDO;
import com.xiaomizhou.dpsk.utils.JsonUtils;
import com.xiaomizhou.dpsk.utils.SequenceUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 工作流模板业务处理类。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/25
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WorkflowTemplateComponent {

    /** 模板编码前缀 */
    private static final String WFT_PREFIX = "WFT";

    private final WorkflowTemplateDao workflowTemplateDao;

    /**
     * 创建工作流模板。
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkflowTemplateDto create(WorkflowTemplateCreateCmd cmd, String ownerCode) {
        String code = SequenceUtils.generator().next(WFT_PREFIX);

        // 计算节点数量
        int nodeCount = calcNodeCount(cmd.getWorkflowJson());

        WorkflowTemplateDO entity = new WorkflowTemplateDO();
        entity.setCode(code);
        entity.setName(cmd.getName());
        entity.setDescription(StringUtils.defaultString(cmd.getDescription()));
        entity.setCategory(cmd.getCategory());
        entity.setNodeCount(nodeCount);
        entity.setStatus(WorkflowTemplateDO.STATUS_DRAFT);
        entity.setVersion(1);
        entity.setWorkflowJson(cmd.getWorkflowJson());
        entity.setInputSchema(StringUtils.defaultString(cmd.getInputSchema()));
        entity.setTimeoutSeconds(cmd.getTimeoutSeconds() != null ? cmd.getTimeoutSeconds() : 3600);
        entity.setFailureStrategy(cmd.getFailureStrategy() != null ? cmd.getFailureStrategy() : 0);
        entity.setMaxRetry(cmd.getMaxRetry() != null ? cmd.getMaxRetry() : 0);
        entity.setNotifyOnComplete(cmd.getNotifyOnComplete() != null ? cmd.getNotifyOnComplete() : 0);
        entity.setOwnerCode(ownerCode);
        entity.setCreateTime(new Date());
        entity.setUpdateTime(new Date());
        entity.setAvatar(cmd.getAvatar());

        workflowTemplateDao.save(entity);
        log.info("创建工作流模板成功: code={}, name={}", code, cmd.getName());

        return convertToDto(entity);
    }

    /**
     * 更新工作流模板（版本号 +1）。
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkflowTemplateDto update(WorkflowTemplateUpdateCmd cmd) {
        WorkflowTemplateDO existing = workflowTemplateDao.getOne(
                new LambdaQueryWrapper<WorkflowTemplateDO>()
                        .eq(WorkflowTemplateDO::getCode, cmd.getId())
                        .eq(WorkflowTemplateDO::getIsDeleted, 0));
        if (existing == null) {
            throw BusinessException.notFound("工作流模板不存在: " + cmd.getId());
        }

        if (StringUtils.isNotBlank(cmd.getName())) {
            existing.setName(cmd.getName());
        }
        if (cmd.getDescription() != null) {
            existing.setDescription(cmd.getDescription());
        }
        if (cmd.getCategory() != null) {
            existing.setCategory(cmd.getCategory());
        }
        if (StringUtils.isNotBlank(cmd.getWorkflowJson())) {
            existing.setWorkflowJson(cmd.getWorkflowJson());
            existing.setNodeCount(calcNodeCount(cmd.getWorkflowJson()));
        }
        if (cmd.getInputSchema() != null) {
            existing.setInputSchema(cmd.getInputSchema());
        }
        if (cmd.getTimeoutSeconds() != null) {
            existing.setTimeoutSeconds(cmd.getTimeoutSeconds());
        }
        if (cmd.getFailureStrategy() != null) {
            existing.setFailureStrategy(cmd.getFailureStrategy());
        }
        if (cmd.getMaxRetry() != null) {
            existing.setMaxRetry(cmd.getMaxRetry());
        }
        if (cmd.getNotifyOnComplete() != null) {
            existing.setNotifyOnComplete(cmd.getNotifyOnComplete());
        }
        if (cmd.getStatus() != null) {
            existing.setStatus(cmd.getStatus());
        }

        if(StringUtils.isNotBlank(cmd.getAvatar())){
            existing.setAvatar(cmd.getAvatar());
        }


        existing.setVersion(existing.getVersion() + 1);
        existing.setUpdateTime(new Date());

        workflowTemplateDao.updateById(existing);
        log.debug("更新工作流模板成功: code={}, version={}", cmd.getId(), existing.getVersion());

        return convertToDto(existing);
    }

    /**
     * 删除工作流模板（逻辑删除）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String code) {
        boolean result = workflowTemplateDao.lambdaUpdate()
                .eq(WorkflowTemplateDO::getCode, code)
                .eq(WorkflowTemplateDO::getIsDeleted, 0)
                .set(WorkflowTemplateDO::getIsDeleted, 1)
                .set(WorkflowTemplateDO::getUpdateTime, new Date())
                .update();
        if (!result) {
            throw BusinessException.notFound("工作流模板不存在: " + code);
        }
        log.debug("删除工作流模板成功: code={}", code);
    }

    /**
     * 根据编码查询模板。
     */
    public WorkflowTemplateDto getByCode(String code) {
        WorkflowTemplateDO entity = workflowTemplateDao.getOne(
                new LambdaQueryWrapper<WorkflowTemplateDO>()
                        .eq(WorkflowTemplateDO::getCode, code)
                        .eq(WorkflowTemplateDO::getIsDeleted, 0));
        return convertToDto(entity);
    }

    /**
     * 分页查询模板列表。
     */
    public ImmutablePair<Long, List<WorkflowTemplateDto>> page(WorkflowTemplateQueryParam param,int pageNo,int pageSize) {
        LambdaQueryWrapper<WorkflowTemplateDO> wrapper = new LambdaQueryWrapper<WorkflowTemplateDO>()
                .eq(WorkflowTemplateDO::getIsDeleted, 0);

        if (param.getCategory() != null) {
            wrapper.eq(WorkflowTemplateDO::getCategory, param.getCategory());
        }
        if (param.getStatus() != null) {
            wrapper.eq(WorkflowTemplateDO::getStatus, param.getStatus());
        }
        if (StringUtils.isNotBlank(param.getKeyword())) {
            wrapper.like(WorkflowTemplateDO::getName, param.getKeyword());
        }

        long cnt = workflowTemplateDao.count(wrapper);
        if (cnt == 0) {
            return ImmutablePair.of(0L, List.of());
        }

        int pn = pageNo > 0 ? pageNo : 1;
        int ps = pageSize > 0 ? pageSize : 10;

        List<WorkflowTemplateDO> list = workflowTemplateDao.list(
                wrapper.last("limit %s,%s".formatted((pn - 1) * ps, ps))
                        .orderByDesc(WorkflowTemplateDO::getCreateTime));

        List<WorkflowTemplateDto> dtos = list.stream().map(this::convertToDto).toList();
        return ImmutablePair.of(cnt, dtos);
    }

    /**
     * 获取模板分类枚举列表（供前端 Tab 展示）。
     * 返回所有分类及其名称。
     */
    public List<Map<String, Object>> getCategories() {
        List<Map<String, Object>> categories = new ArrayList<>();
        for (int i = WorkflowTemplateDO.CATEGORY_GENERAL; i <= WorkflowTemplateDO.CATEGORY_OTHER; i++) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("code", i);
            map.put("name", WorkflowTemplateDO.categoryName(i));
            categories.add(map);
        }
        return categories;
    }

    // ======================== 模型转换 ========================

    private WorkflowTemplateDto convertToDto(WorkflowTemplateDO entity) {
        if (entity == null) return null;

        WorkflowTemplateDto dto = new WorkflowTemplateDto();
        dto.setId(entity.getCode());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setCategory(entity.getCategory());
        dto.setCategoryName(WorkflowTemplateDO.categoryName(entity.getCategory()));
        dto.setNodeCount(entity.getNodeCount());
        dto.setStatus(entity.getStatus());
        dto.setStatusName(WorkflowTemplateDO.statusName(entity.getStatus()));
        dto.setVersion(entity.getVersion());
        dto.setWorkflowJson(entity.getWorkflowJson());
        dto.setInputSchema(entity.getInputSchema());
        dto.setTimeoutSeconds(entity.getTimeoutSeconds());
        dto.setFailureStrategy(entity.getFailureStrategy());
        dto.setMaxRetry(entity.getMaxRetry());
        dto.setNotifyOnComplete(entity.getNotifyOnComplete());
        dto.setOwnerCode(entity.getOwnerCode());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setAvatar(entity.getAvatar());
        return dto;
    }

    // ======================== 工具方法 ========================

    /**
     * 从 workflow_json 中计算节点数量。
     * 假设 JSON 结构为 { "nodes": [...], "edges": [...] }
     */
    @SuppressWarnings("unchecked")
    private static int calcNodeCount(String workflowJson) {
        if (StringUtils.isBlank(workflowJson)) return 0;
        try {
            Map<String, Object> json = JsonUtils.toObj(workflowJson,
                    new TypeReference<Map<String, Object>>() {});
            if (json != null && json.containsKey("nodes")) {
                Object nodes = json.get("nodes");
                if (nodes instanceof List) {
                    return ((List<?>) nodes).size();
                }
            }
        } catch (Exception e) {
            log.warn("计算节点数量失败: {}", workflowJson, e);
        }
        return 0;
    }
}

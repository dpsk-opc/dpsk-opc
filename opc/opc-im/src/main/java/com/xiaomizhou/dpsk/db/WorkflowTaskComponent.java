package com.xiaomizhou.dpsk.db;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiaomizhou.dpsk.constant.ConversationType;
import com.xiaomizhou.dpsk.core.exceptions.BusinessException;
import com.xiaomizhou.dpsk.db.dao.ConversationDao;
import com.xiaomizhou.dpsk.db.dao.WorkflowTaskDao;
import com.xiaomizhou.dpsk.db.dao.WorkflowTemplateDao;
import com.xiaomizhou.dpsk.db.dto.WorkflowTaskDto;
import com.xiaomizhou.dpsk.db.dto.WorkflowTaskQueryParam;
import com.xiaomizhou.dpsk.db.dto.WorkflowTemplateDto;
import com.xiaomizhou.dpsk.db.model.Conversation;
import com.xiaomizhou.dpsk.db.model.WorkflowTaskDO;
import com.xiaomizhou.dpsk.db.model.WorkflowTemplateDO;
import com.xiaomizhou.dpsk.utils.SequenceUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Objects;

import static com.xiaomizhou.dpsk.utils.SequenceUtils.UUIDSequenceGenerator.CONVERSATION_PREFIX;

/**
 * 工作流任务业务处理类。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/25
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WorkflowTaskComponent {

    /** 任务编码前缀 */
    private static final String WFK_PREFIX = "WFK";

    private final WorkflowTaskDao workflowTaskDao;

    private final WorkflowTemplateComponent workflowTemplateComponent;

    private final ConversationDao conversationDao;


    /**
     * 根据编码查询任务。
     */
    public WorkflowTaskDto getByCode(String code) {
        WorkflowTaskDO entity = workflowTaskDao.getOne(
                new LambdaQueryWrapper<WorkflowTaskDO>()
                        .eq(WorkflowTaskDO::getCode, code)
                        .eq(WorkflowTaskDO::getIsDeleted, 0));
        return convertToDto(entity);
    }

    /**
     * 分页查询任务列表。
     */
    public ImmutablePair<Long, List<WorkflowTaskDto>> page(WorkflowTaskQueryParam param,int pageNo,int pageSize) {
        LambdaQueryWrapper<WorkflowTaskDO> wrapper = new LambdaQueryWrapper<WorkflowTaskDO>()
                .eq(WorkflowTaskDO::getIsDeleted, 0);

        if (StringUtils.isNotBlank(param.getTemplateCode())) {
            wrapper.eq(WorkflowTaskDO::getTemplateCode, param.getTemplateCode());
        }
        if (param.getStatus() != null) {
            wrapper.eq(WorkflowTaskDO::getStatus, param.getStatus());
        }
        if (StringUtils.isNotBlank(param.getKeyword())) {
            wrapper.like(WorkflowTaskDO::getName, param.getKeyword());
        }

        long cnt = workflowTaskDao.count(wrapper);
        if (cnt == 0) {
            return ImmutablePair.of(0L, List.of());
        }

        int pn = pageNo > 0 ? pageNo : 1;
        int ps = pageSize > 0 ? pageSize : 10;

        List<WorkflowTaskDO> list = workflowTaskDao.list(
                wrapper.last("limit %s,%s".formatted((pn - 1) * ps, ps))
                        .orderByDesc(WorkflowTaskDO::getCreateTime));

        List<WorkflowTaskDto> dtos = list.stream().map(this::convertToDto).toList();
        return ImmutablePair.of(cnt, dtos);
    }

    /**
     * 取消任务。
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(String code) {
        WorkflowTaskDO existing = workflowTaskDao.getOne(
                new LambdaQueryWrapper<WorkflowTaskDO>()
                        .eq(WorkflowTaskDO::getCode, code)
                        .eq(WorkflowTaskDO::getIsDeleted, 0));
        if (existing == null) {
            throw BusinessException.notFound("工作流任务不存在: " + code);
        }
        if (existing.getStatus() != WorkflowTaskDO.STATUS_PENDING
                && existing.getStatus() != WorkflowTaskDO.STATUS_RUNNING
                && existing.getStatus() != WorkflowTaskDO.STATUS_APPROVING) {
            throw BusinessException.businessError("当前状态不允许取消");
        }

        workflowTaskDao.lambdaUpdate()
                .eq(WorkflowTaskDO::getCode, code)
                .set(WorkflowTaskDO::getStatus, WorkflowTaskDO.STATUS_CANCELLED)
                .set(WorkflowTaskDO::getEndTime, new Date())
                .set(WorkflowTaskDO::getUpdateTime, new Date())
                .update();
        log.info("取消工作流任务成功: code={}", code);
    }

    /**
     * 删除任务（逻辑删除）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String code) {
        boolean result = workflowTaskDao.lambdaUpdate()
                .eq(WorkflowTaskDO::getCode, code)
                .eq(WorkflowTaskDO::getIsDeleted, 0)
                .set(WorkflowTaskDO::getIsDeleted, 1)
                .set(WorkflowTaskDO::getUpdateTime, new Date())
                .update();
        if (!result) {
            throw BusinessException.notFound("工作流任务不存在: " + code);
        }
        log.info("删除工作流任务成功: code={}", code);
    }

    // ======================== 模型转换 ========================

    private WorkflowTaskDto convertToDto(WorkflowTaskDO entity) {
        if (entity == null) return null;

        WorkflowTaskDto dto = new WorkflowTaskDto();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setTemplateCode(entity.getTemplateCode());
        dto.setTemplateVersion(entity.getTemplateVersion());
        dto.setStatus(entity.getStatus());
        dto.setStatusName(WorkflowTaskDO.statusName(entity.getStatus()));
        dto.setCurrentNodeId(entity.getCurrentNodeId());
        dto.setCurrentStep(entity.getCurrentStep());
        dto.setWorkflowJson(entity.getWorkflowJson());
        dto.setInputParams(entity.getInputParams());
        dto.setContextData(entity.getContextData());
        dto.setStartTime(entity.getStartTime());
        dto.setEndTime(entity.getEndTime());
        dto.setErrorMessage(entity.getErrorMessage());
        dto.setSource(entity.getSource());
        dto.setSourceName(WorkflowTaskDO.sourceName(entity.getSource()));
        dto.setConversationCode(entity.getConversationCode());
        dto.setAgentCode(entity.getAgentCode());
        dto.setScheduledTaskCode(entity.getScheduledTaskCode());
        dto.setOwnerCode(entity.getOwnerCode());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setAvatar(entity.getAvatar());
        return dto;
    }

    /**
     * 添加工作流任务。
     *
     * @param task
     * @return
     */
    public WorkflowTaskDto add(WorkflowTaskDto task) {


        WorkflowTaskDO model = new WorkflowTaskDO();

        model.setCode(SequenceUtils.generator().next(WFK_PREFIX));
        model.setName(task.getName());
        model.setOwnerCode(task.getOwnerCode());

        String templateCode = task.getTemplateCode();

        WorkflowTemplateDto template = workflowTemplateComponent.getByCode(templateCode);
        if (Objects.isNull(template) || template.getStatus() != WorkflowTemplateDO.STATUS_PUBLISHED) {
            throw BusinessException.paramError("工作流模板不存在或者不是发布状态");
        }

        model.setContextData(task.getContextData());
        model.setTemplateCode(templateCode);
        model.setWorkflowJson(template.getWorkflowJson());
        model.setTemplateVersion(template.getVersion());
        model.setCreateTime(new Date());
        model.setUpdateTime(new Date());
        model.setStatus(WorkflowTaskDO.STATUS_PENDING);
        model.setSource(WorkflowTaskDO.SOURCE_USER);
        if (StringUtils.isBlank(task.getAvatar())) {
            model.setAvatar(template.getAvatar());
        }

        model.setInputParams("");
        model.setContextData("");
        model.setScheduledTaskCode("");
        model.setAgentCode("");
        model.setCurrentNodeId("");
        model.setCurrentStep(0);
        model.setErrorMessage("");

        workflowTaskDao.save(model);


        Conversation conversation = new Conversation();

        conversation.setCode(SequenceUtils.generator().next(CONVERSATION_PREFIX));
        conversation.setConversationType(ConversationType.WORKFLOW.getCode());
        conversation.setTargetCode(model.getCode());
        conversation.setOwnerCode(model.getOwnerCode());
        conversation.setLastUserMessageCode(model.getOwnerCode());
        conversation.setCreateTime(new Date());
        conversation.setUpdateTime(new Date());
        conversationDao.save(conversation);

        WorkflowTaskDto result = new WorkflowTaskDto();

        result.setCode(model.getCode());
        result.setConversationCode(conversation.getCode());
        result.setAvatar(model.getAvatar());

        return result;
    }
}

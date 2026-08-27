package com.xiaomizhou.dpsk.db;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
import org.springframework.beans.BeanUtils;
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

        if (StringUtils.isNotBlank(param.getConversationCode())) {
            wrapper.eq(WorkflowTaskDO::getConversationCode, param.getConversationCode());
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
        dto.setTaskInfo(entity.getTaskInfo());
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
        model.setOwnerCode(task.getOwnerCode());

        String templateCode = task.getTemplateCode();

        WorkflowTemplateDto template = workflowTemplateComponent.getByCode(templateCode);
        if (Objects.isNull(template) || template.getStatus() != WorkflowTemplateDO.STATUS_PUBLISHED) {
            throw BusinessException.paramError("工作流模板不存在或者不是发布状态");
        }

        if (StringUtils.isNotBlank(task.getConversationCode())) {
            model.setConversationCode(task.getConversationCode());
        } else {

            Conversation exits = conversationDao.getOne(task.getOwnerCode(), templateCode, ConversationType.WORKFLOW.getCode());

            if (Objects.isNull(exits)) {

                Conversation conversation = new Conversation();
                conversation.setCode(SequenceUtils.generator().next(CONVERSATION_PREFIX));
                conversation.setConversationType(ConversationType.WORKFLOW.getCode());
                conversation.setTargetCode(templateCode);
                conversation.setOwnerCode(task.getOwnerCode());
                conversation.setCreateTime(new Date());
                conversation.setUpdateTime(new Date());
                conversationDao.save(conversation);

                model.setConversationCode(conversation.getCode());
            } else {
                model.setConversationCode(exits.getCode());
            }
        }

        model.setContextData(task.getContextData());
        model.setTemplateCode(templateCode);
        model.setWorkflowJson(template.getWorkflowJson());
        model.setTemplateVersion(template.getVersion());
        model.setCreateTime(new Date());
        model.setUpdateTime(new Date());
        model.setStatus(WorkflowTaskDO.STATUS_PENDING);
        model.setSource(WorkflowTaskDO.SOURCE_USER);
        model.setName(task.getName());
        model.setTaskInfo(task.getContextData());


        if (StringUtils.isBlank(task.getAvatar())) {
            model.setAvatar(template.getAvatar());
        } else {
            model.setAvatar(task.getAvatar());
        }

        model.setInputParams("");
        model.setContextData("");
        model.setScheduledTaskCode("");
        model.setAgentCode("");
        model.setCurrentNodeId("");
        model.setCurrentStep(0);
        model.setErrorMessage("");

        workflowTaskDao.save(model);




        WorkflowTaskDto result = new WorkflowTaskDto();

        result.setCode(model.getCode());
        result.setAvatar(model.getAvatar());
        result.setConversationCode(model.getConversationCode());

        // 专家团的名字作为会话名字
        result.setName(template.getName());

        return result;
    }

    public boolean updateByCode(WorkflowTaskDto dto) {
        if (Objects.isNull(dto) || StringUtils.isBlank(dto.getCode())) {
            return false;
        }
        WorkflowTaskDO model = new WorkflowTaskDO();
        BeanUtils.copyProperties(dto, model);
        return workflowTaskDao.update(model, Wrappers.<WorkflowTaskDO>lambdaUpdate()
                .eq(WorkflowTaskDO::getCode, dto.getCode()));
    }

    /**
     * 无模板落库（群聊自主规划专用）：不校验 templateCode 发布状态，
     * workflow_json 直接取入参；source = SOURCE_AGENT；返回落库后的任务 DTO（含 code）。
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkflowTaskDto addByPlan(WorkflowTaskDto task, String workflowJson) {
        WorkflowTaskDO model = new WorkflowTaskDO();
        model.setCode(SequenceUtils.generator().next(WFK_PREFIX));
        model.setName(task.getName());
        model.setOwnerCode(task.getOwnerCode());
        model.setConversationCode(task.getConversationCode());
        model.setAgentCode(task.getAgentCode());
        model.setWorkflowJson(workflowJson);
        model.setContextData(task.getContextData());
        model.setTemplateCode("");
        model.setTemplateVersion(0);
        model.setStatus(WorkflowTaskDO.STATUS_PENDING);
        model.setSource(WorkflowTaskDO.SOURCE_AGENT);
        model.setAvatar(StringUtils.isNotBlank(task.getAvatar()) ? task.getAvatar() : "");
        model.setInputParams("");
        model.setScheduledTaskCode("");
        model.setCurrentNodeId("");
        model.setCurrentStep(0);
        model.setTaskInfo(task.getTaskInfo());
        model.setErrorMessage("");
        model.setCreateTime(new Date());
        model.setUpdateTime(new Date());
        workflowTaskDao.save(model);

        WorkflowTaskDto result = new WorkflowTaskDto();
        result.setCode(model.getCode());
        result.setAvatar(model.getAvatar());
        result.setConversationCode(model.getConversationCode());
        result.setName(model.getName());
        result.setStatus(model.getStatus());
        return result;
    }

    /**
     * replan 覆盖：全量替换 workflow_json + status 回 RUNNING + currentNodeId 清空 + errorMessage 清空。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateWorkflowJsonAndReset(String taskCode, String workflowJson) {
        workflowTaskDao.lambdaUpdate()
                .eq(WorkflowTaskDO::getCode, taskCode)
                .eq(WorkflowTaskDO::getIsDeleted, 0)
                .set(WorkflowTaskDO::getWorkflowJson, workflowJson)
                .set(WorkflowTaskDO::getStatus, WorkflowTaskDO.STATUS_RUNNING)
                .set(WorkflowTaskDO::getCurrentNodeId, "")
                .set(WorkflowTaskDO::getCurrentStep, 0)
                .set(WorkflowTaskDO::getErrorMessage, "")
                .set(WorkflowTaskDO::getUpdateTime, new Date())
                .update();
        log.info("replan workflow json reset. taskCode={}", taskCode);
    }

    /**
     * 任务成功收尾：status → SUCCESS，记录结束时间。
     */
    @Transactional(rollbackFor = Exception.class)
    public void completeByCode(String taskCode) {
        workflowTaskDao.lambdaUpdate()
                .eq(WorkflowTaskDO::getCode, taskCode)
                .eq(WorkflowTaskDO::getIsDeleted, 0)
                .set(WorkflowTaskDO::getStatus, WorkflowTaskDO.STATUS_SUCCESS)
                .set(WorkflowTaskDO::getErrorMessage, "")
                .set(WorkflowTaskDO::getEndTime, new Date())
                .set(WorkflowTaskDO::getUpdateTime, new Date())
                .update();
        log.info("workflow task completed. taskCode={}", taskCode);
    }

    /**
     * 任务失败收尾：status → FAILED，记录失败原因。
     */
    @Transactional(rollbackFor = Exception.class)
    public void failByCode(String taskCode, String errorMessage) {
        workflowTaskDao.lambdaUpdate()
                .eq(WorkflowTaskDO::getCode, taskCode)
                .eq(WorkflowTaskDO::getIsDeleted, 0)
                .set(WorkflowTaskDO::getStatus, WorkflowTaskDO.STATUS_FAILED)
                .set(WorkflowTaskDO::getErrorMessage, errorMessage)
                .set(WorkflowTaskDO::getEndTime, new Date())
                .set(WorkflowTaskDO::getUpdateTime, new Date())
                .update();
        log.info("workflow task failed. taskCode={}, err={}", taskCode, errorMessage);
    }

    /**
     * 更新工作流任务。
     *
     * @param task
     * @return
     */
    public String update(WorkflowTaskDto task) {

        if (Objects.isNull(task) || StringUtils.isBlank(task.getName())) {
            return null;
        }


        String taskCode = task.getCode();

        WorkflowTaskDO model = new WorkflowTaskDO();

        model.setName(task.getName());
        model.setUpdateTime(new Date());

        workflowTaskDao.update(model, Wrappers.<WorkflowTaskDO>lambdaUpdate()
                .eq(WorkflowTaskDO::getCode, taskCode));
        return taskCode;
    }
}

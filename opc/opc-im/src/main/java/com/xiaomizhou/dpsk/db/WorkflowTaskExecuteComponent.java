package com.xiaomizhou.dpsk.db;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xiaomizhou.dpsk.db.dao.WorkflowNodeLogDao;
import com.xiaomizhou.dpsk.db.dto.WorkflowTaskDto;
import com.xiaomizhou.dpsk.db.model.WorkflowNodeLogDO;
import com.xiaomizhou.dpsk.db.model.WorkflowTaskDO;
import com.xiaomizhou.dpsk.workflow.NodeContext;
import com.xiaomizhou.dpsk.workflow.xyflow.NodeStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Date;


@Component
@Slf4j
@RequiredArgsConstructor
public class WorkflowTaskExecuteComponent {

    private final WorkflowNodeLogDao workflowNodeLogDao;

    private final WorkflowTaskComponent workflowTaskComponent;

    public WorkflowNodeLogDO getOne(String taskCode, String nodeId) {

        if (StringUtils.isAnyBlank(taskCode, nodeId)) {
            return null;
        }

        return workflowNodeLogDao.getOne(Wrappers.<WorkflowNodeLogDO>lambdaQuery()
                .eq(WorkflowNodeLogDO::getTaskCode, taskCode)
                .eq(WorkflowNodeLogDO::getNodeId, nodeId)
                .last("order by id desc limit 1"));
    }

    public Long start(NodeContext node, String taskCode, String agent, String inputData) {

        WorkflowNodeLogDO log = new WorkflowNodeLogDO();
        log.setNodeId(node.getNodeId());

        Integer type = NodeStep.getNodeTypeByName(node.getNodeType());
        log.setNodeType(type);
        log.setNodeName(node.getNodeLabel());
        log.setTaskCode(taskCode);
        log.setStatus(WorkflowNodeLogDO.STATUS_RUNNING);
        log.setStartTime(new Date());
        log.setCreateTime(new Date());
        log.setUpdateTime(new Date());
        log.setAgentCode(agent);
        log.setInputData(inputData);

        workflowNodeLogDao.save(log);

        if (node.getNodeType().equals(NodeStep.NODE_TYPE_START.left)) {
            WorkflowTaskDto dto = new WorkflowTaskDto();
            dto.setCurrentNodeId(node.getNodeId());
            dto.setStartTime(new Date());
            dto.setUpdateTime(new Date());
            dto.setStatus(WorkflowTaskDO.STATUS_RUNNING);
            dto.setCode(taskCode);
            dto.setContextData(inputData);

            workflowTaskComponent.updateByCode(dto);
        }
        return log.getId();
    }

    public boolean end(Long id, Integer nodeStatus, Integer taskStatus, String error, String taskOutput,String nodeOutput) {

        WorkflowNodeLogDO log = workflowNodeLogDao.getById(id);
        log.setStatus(nodeStatus);
        log.setEndTime(new Date());
        log.setUpdateTime(new Date());
        log.setOutputData(nodeOutput);
        log.setErrorMessage(error);
        log.setTaskCode(log.getTaskCode());

        workflowNodeLogDao.updateById(log);


        WorkflowTaskDto dto = new WorkflowTaskDto();
        dto.setCurrentNodeId(log.getNodeId());
        dto.setCode(log.getTaskCode());
        dto.setUpdateTime(new Date());
        dto.setErrorMessage(error);
        dto.setContextData(taskOutput);

        // 终止节点更新状态为成功
        if (log.getNodeType() == WorkflowNodeLogDO.TYPE_END) {
            dto.setEndTime(new Date());
        }

        dto.setStatus(taskStatus);
        workflowTaskComponent.updateByCode(dto);
        return true;
    }
}

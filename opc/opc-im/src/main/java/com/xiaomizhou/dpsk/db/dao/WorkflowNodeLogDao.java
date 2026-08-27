package com.xiaomizhou.dpsk.db.dao;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaomizhou.dpsk.db.mapper.WorkflowNodeLogMapper;
import com.xiaomizhou.dpsk.db.model.WorkflowNodeLogDO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 工作流节点日志 DAO。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/25
 */
@Component
@Slf4j
public class WorkflowNodeLogDao extends ServiceImpl<WorkflowNodeLogMapper, WorkflowNodeLogDO> {


    public List<WorkflowNodeLogDO> getLogs(String taskCode) {
        // 按 taskCode 查节点日志，按创建时间升序取"已到达节点"（运行中/成功/失败/跳过）
        return getLogsByTaskCodes(Collections.singletonList(taskCode)).get(taskCode);
    }

    public Map<String, List<WorkflowNodeLogDO>> getLogsByTaskCodes(List<String> taskCodes) {

        if (CollectionUtils.isEmpty(taskCodes)) {
            return Collections.emptyMap();
        }

        List<WorkflowNodeLogDO> logs = list(Wrappers.<WorkflowNodeLogDO>lambdaQuery().in(WorkflowNodeLogDO::getTaskCode, taskCodes).orderByAsc(WorkflowNodeLogDO::getId));
        return logs.stream().collect(Collectors.groupingBy(WorkflowNodeLogDO::getTaskCode));
    }



}

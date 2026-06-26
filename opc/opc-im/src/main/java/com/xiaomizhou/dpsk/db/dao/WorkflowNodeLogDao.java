package com.xiaomizhou.dpsk.db.dao;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaomizhou.dpsk.db.mapper.WorkflowNodeLogMapper;
import com.xiaomizhou.dpsk.db.model.WorkflowNodeLogDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 工作流节点日志 DAO。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/25
 */
@Component
@Slf4j
public class WorkflowNodeLogDao extends ServiceImpl<WorkflowNodeLogMapper, WorkflowNodeLogDO> {
}

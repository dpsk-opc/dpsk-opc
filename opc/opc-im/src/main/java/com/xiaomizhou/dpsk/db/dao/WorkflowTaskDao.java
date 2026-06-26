package com.xiaomizhou.dpsk.db.dao;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaomizhou.dpsk.db.mapper.WorkflowTaskMapper;
import com.xiaomizhou.dpsk.db.model.WorkflowTaskDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 工作流任务 DAO。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/25
 */
@Component
@Slf4j
public class WorkflowTaskDao extends ServiceImpl<WorkflowTaskMapper, WorkflowTaskDO> {
}

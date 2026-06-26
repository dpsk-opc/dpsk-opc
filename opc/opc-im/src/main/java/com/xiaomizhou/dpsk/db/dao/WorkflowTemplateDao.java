package com.xiaomizhou.dpsk.db.dao;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaomizhou.dpsk.db.mapper.WorkflowTemplateMapper;
import com.xiaomizhou.dpsk.db.model.WorkflowTemplateDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 工作流模板 DAO。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/25
 */
@Component
@Slf4j
public class WorkflowTemplateDao extends ServiceImpl<WorkflowTemplateMapper, WorkflowTemplateDO> {
}

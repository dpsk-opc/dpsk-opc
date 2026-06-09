package com.xiaomizhou.dpsk.db.dao;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaomizhou.dpsk.db.mapper.TaskExecutionLogMapper;
import com.xiaomizhou.dpsk.db.model.TaskExecutionLogDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * 任务执行日志 DAO。
 *
 * @author eason - vipzhsh@163.com
 */
@Component
@Slf4j
public class TaskExecutionLogDao extends ServiceImpl<TaskExecutionLogMapper, TaskExecutionLogDO> {

    /** 保存 */
    public boolean save(TaskExecutionLogDO entity) {
        return super.save(entity);
    }

    /** 根据 ID 更新 */
    public boolean updateById(TaskExecutionLogDO entity) {
        entity.setUpdateTime(new Date());
        return super.updateById(entity);
    }

    /** 根据任务编码查询（按时间降序） */
    public List<TaskExecutionLogDO> findByTaskCode(String taskCode, int limit) {
        return lambdaQuery()
                .eq(TaskExecutionLogDO::getTaskCode, taskCode)
                .eq(TaskExecutionLogDO::getIsDeleted, 0)
                .orderByDesc(TaskExecutionLogDO::getStartTime)
                .last("LIMIT " + limit)
                .list();
    }

    /** 根据消费者标识查询（按时间降序） */
    public List<TaskExecutionLogDO> findByConsumerKey(String consumerKey, int limit) {
        return lambdaQuery()
                .eq(TaskExecutionLogDO::getConsumerKey, consumerKey)
                .eq(TaskExecutionLogDO::getIsDeleted, 0)
                .orderByDesc(TaskExecutionLogDO::getStartTime)
                .last("LIMIT " + limit)
                .list();
    }

    /** 根据 ID 查询 */
    public TaskExecutionLogDO getById(Long id) {
        return super.getById(id);
    }
}

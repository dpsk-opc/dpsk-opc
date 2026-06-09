package com.xiaomizhou.dpsk.db.dao;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaomizhou.dpsk.db.mapper.TaskMapper;
import com.xiaomizhou.dpsk.db.model.TaskDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * 任务 DAO。
 *
 * @author eason - vipzhsh@163.com
 */
@Component
@Slf4j
public class TaskDao extends ServiceImpl<TaskMapper, TaskDO> {

    /** 根据编码查询 */
    public TaskDO getByCode(String code) {
        return lambdaQuery()
                .eq(TaskDO::getCode, code)
                .eq(TaskDO::getIsDeleted, 0)
                .one();
    }

    /** 查询所有启用的定时任务 */
    public List<TaskDO> findAllEnabledScheduled() {
        return lambdaQuery()
                .eq(TaskDO::getTaskType, "SCHEDULED")
                .eq(TaskDO::getStatus, "ENABLED")
                .eq(TaskDO::getIsDeleted, 0)
                .list();
    }

    /** 查询所有启用的任务 */
    public List<TaskDO> findAllEnabled() {
        return lambdaQuery()
                .eq(TaskDO::getStatus, "ENABLED")
                .eq(TaskDO::getIsDeleted, 0)
                .list();
    }

    /** 按类型查询 */
    public List<TaskDO> findByTaskType(String taskType) {
        return lambdaQuery()
                .eq(TaskDO::getTaskType, taskType)
                .eq(TaskDO::getIsDeleted, 0)
                .list();
    }

    /** 保存 */
    public boolean save(TaskDO entity) {
        return super.save(entity);
    }

    /** 根据 ID 更新 */
    public boolean updateById(TaskDO entity) {
        entity.setUpdateTime(new Date());
        return super.updateById(entity);
    }

    /** 根据编码更新状态 */
    public boolean updateStatus(String code, String status) {
        return lambdaUpdate()
                .eq(TaskDO::getCode, code)
                .set(TaskDO::getStatus, status)
                .set(TaskDO::getUpdateTime, new Date())
                .update();
    }

    /** 根据编码逻辑删除 */
    public boolean deleteByCode(String code) {
        return lambdaUpdate()
                .eq(TaskDO::getCode, code)
                .set(TaskDO::getIsDeleted, 1)
                .set(TaskDO::getUpdateTime, new Date())
                .update();
    }

    /** 检查编码是否存在 */
    public boolean existsByCode(String code) {
        return lambdaQuery()
                .eq(TaskDO::getCode, code)
                .eq(TaskDO::getIsDeleted, 0)
                .count() > 0;
    }
}

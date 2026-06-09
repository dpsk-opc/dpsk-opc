package com.xiaomizhou.dpsk.db.dao;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaomizhou.dpsk.db.mapper.AgentMapper;
import com.xiaomizhou.dpsk.db.model.Agent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * Agent DAO
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/18 18:11
 */
@Component
@Slf4j
public class AgentDao extends ServiceImpl<AgentMapper, Agent> {

    /**
     * 根据ID查询
     */
    public Agent getById(Long id) {
        return super.getById(id);
    }

    /**
     * 根据业务编码查询
     */
    public Agent getByCode(String code) {
        return super.lambdaQuery()
                .eq(Agent::getCode, code)
                .eq(Agent::getIsDeleted, 0)
                .one();
    }

    /**
     * 根据邮箱查询
     */
    public Agent getByEmail(String email) {
        return super.lambdaQuery()
                .eq(Agent::getEmail, email)
                .eq(Agent::getIsDeleted, 0)
                .one();
    }

    /**
     * 保存
     */
    public boolean save(Agent agent) {
        return super.save(agent);
    }

    /**
     * 批量保存
     */
    public boolean saveBatch(List<Agent> agents) {
        return super.saveBatch(agents);
    }

    /**
     * 更新
     */
    public boolean updateById(Agent agent) {
        return super.updateById(agent);
    }

    /**
     * 根据ID删除（逻辑删除）
     */
    public boolean removeById(Long id) {
        Agent agent = new Agent();
        agent.setId(id);
        agent.setIsDeleted(1);
        return super.updateById(agent);
    }

    /**
     * 根据业务编码删除（逻辑删除）
     */
    public boolean removeByCode(String code) {
        return super.lambdaUpdate()
                .eq(Agent::getCode, code)
                .set(Agent::getIsDeleted, 1)
                .update();
    }

    /**
     * 根据类型查询
     */
    public List<Agent> findByType(String type) {
        return super.lambdaQuery()
                .eq(Agent::getType, type)
                .eq(Agent::getIsDeleted, 0)
                .list();
    }

    /**
     * 根据状态查询
     */
    public List<Agent> findByStatus(String status) {
        return super.lambdaQuery()
                .eq(Agent::getStatus, status)
                .eq(Agent::getIsDeleted, 0)
                .list();
    }

    /**
     * 查询所有活跃的Agent
     */
    public List<Agent> findAllActive() {
        return super.lambdaQuery()
                .eq(Agent::getStatus, "ACTIVE")
                .eq(Agent::getIsDeleted, 0)
                .list();
    }

    /**
     * 根据角色查询
     */
    public List<Agent> findByRole(String role) {
        return super.lambdaQuery()
                .eq(Agent::getRole, role)
                .eq(Agent::getIsDeleted, 0)
                .list();
    }

    /**
     * 更新最后活跃时间
     */
    public boolean updateLastActiveTime(Long id) {
        Agent agent = new Agent();
        agent.setId(id);
        agent.setLastActiveTime(new Date());
        agent.setUpdateTime(new Date());
        return super.updateById(agent);
    }

    /**
     * 关键词搜索（名称、昵称、描述）
     */
    public List<Agent> search(String keyword) {
        return super.lambdaQuery()
                .and(wrapper -> wrapper
                        .like(Agent::getName, keyword)
                        .or()
                        .like(Agent::getNickname, keyword)
                        .or()
                        .like(Agent::getDescription, keyword))
                .eq(Agent::getIsDeleted, 0)
                .list();
    }

    /**
     * 条件查询
     */
    public List<Agent> findByCondition(String type, String status, String role) {
        return super.lambdaQuery()
                .eq(type != null, Agent::getType, type)
                .eq(status != null, Agent::getStatus, status)
                .eq(role != null, Agent::getRole, role)
                .eq(Agent::getIsDeleted, 0)
                .list();
    }

    /**
     * 分页查询（需要传入page和size）
     */
    public List<Agent> findByPage(int page, int size) {
        int offset = (page - 1) * size;
        return super.lambdaQuery()
                .eq(Agent::getIsDeleted, 0)
                .orderByDesc(Agent::getCreateTime)
                .last("LIMIT " + offset + ", " + size)
                .list();
    }

}

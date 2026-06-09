package com.xiaomizhou.dpsk.db.dao;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaomizhou.dpsk.db.mapper.AgentAuthTokenMapper;
import com.xiaomizhou.dpsk.db.model.AgentAuthToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * Agent 认证 Token DAO
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/23
 */
@Component
@Slf4j
public class AgentAuthTokenDao extends ServiceImpl<AgentAuthTokenMapper, AgentAuthToken> {

    /**
     * 根据 agentId 和 token 查询有效记录
     *
     * @param agentId Agent ID
     * @param token   Token 字符串
     * @return 有效的 AgentAuthToken，不存在或已过期返回 null
     */
    public AgentAuthToken getActiveByAgentIdAndToken(Long agentId, String token) {
        return super.lambdaQuery()
                .eq(AgentAuthToken::getAgentCode, agentId)
                .eq(AgentAuthToken::getToken, token)
                .eq(AgentAuthToken::getStatus, "ACTIVE")
                .ge(AgentAuthToken::getExpireTime, new Date())
                .eq(AgentAuthToken::getIsDeleted, 0)
                .one();
    }

    /**
     * 保存或更新 Token（先撤销旧 Token，再插入新 Token）
     *
     * @param tokenRecord 新的 Token 记录
     */
    public void upsertToken(AgentAuthToken tokenRecord) {
        // 1. 撤销该 Agent 下所有 ACTIVE 状态的旧 Token
        super.lambdaUpdate()
                .eq(AgentAuthToken::getAgentCode, tokenRecord.getAgentCode())
                .eq(AgentAuthToken::getStatus, "ACTIVE")
                .eq(AgentAuthToken::getIsDeleted, 0)
                .set(AgentAuthToken::getStatus, "REVOKED")
                .set(AgentAuthToken::getUpdateTime, new Date())
                .update();

        // 2. 插入新 Token
        super.save(tokenRecord);
        log.info("Token 写入成功, agentId={}, code={}", tokenRecord.getAgentCode(), tokenRecord.getCode());
    }

    /**
     * 撤销指定 Agent 的所有 Token
     *
     * @param agentId Agent ID
     */
    public void revokeByAgentId(Long agentId) {
        super.lambdaUpdate()
                .eq(AgentAuthToken::getAgentCode, agentId)
                .eq(AgentAuthToken::getStatus, "ACTIVE")
                .eq(AgentAuthToken::getIsDeleted, 0)
                .set(AgentAuthToken::getStatus, "REVOKED")
                .set(AgentAuthToken::getUpdateTime, new Date())
                .update();
    }
}

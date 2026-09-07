package com.xiaomizhou.dpsk.db;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xiaomizhou.dpsk.db.dao.AgentDao;
import com.xiaomizhou.dpsk.db.dao.ChatMessageDao;
import com.xiaomizhou.dpsk.db.dao.ConversationDao;
import com.xiaomizhou.dpsk.db.dao.TokenUsageDao;
import com.xiaomizhou.dpsk.db.dto.*;
import com.xiaomizhou.dpsk.db.model.Agent;
import com.xiaomizhou.dpsk.db.model.ChatMessage;
import com.xiaomizhou.dpsk.db.model.Conversation;
import com.xiaomizhou.dpsk.db.model.TokenUsage;
import com.xiaomizhou.dpsk.utils.SequenceUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * Token用量管理组件
 *
 * @author eason - vipzhsh@163.com
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TokenUsageComponent {

    private final TokenUsageDao tokenUsageDao;
    private final AgentDao agentDao;
    private final ConversationDao conversationDao;
    private final ChatMessageDao chatMessageDao;

    /**
     * 管理端 - 分页查询Token用量（含关联的会话和消息信息）
     */
    /**
     * 保存一次 LLM 调用的 token 用量。
     * 任何异常都会被吞掉并打 warn，绝不阻断业务主流程。
     */
    public void saveUsage(UsageRecord record) {
        if (record == null || record.getTokenUsage() == null) {
            return;
        }
        try {
            TokenUsage t = new TokenUsage();
            t.setCode(SequenceUtils.generator().next("TKU"));
            t.setAgentCode(record.getAgentCode());
            t.setConversationCode(record.getConversationCode());
            t.setMessageCode(record.getMessageCode());
            t.setTaskId(record.getTaskId());
            t.setUsageType(record.getUsageType());
            t.setModelName(record.getModelName());
            t.setProvider(record.getProvider());
            t.setInputTokens(record.getTokenUsage().inputTokenCount());
            t.setOutputTokens(record.getTokenUsage().outputTokenCount());
            t.setTotalTokens(record.getTokenUsage().totalTokenCount());
            t.setCreateTime(new Date());
            t.setUpdateTime(new Date());
            tokenUsageDao.save(t);
        } catch (Exception e) {
            log.warn("save token usage failed, agentCode={}, usageType={}", record.getAgentCode(), record.getUsageType(), e);
        }
    }

    public ImmutablePair<Long, List<TokenUsageDto>> page(TokenUsagePageCmd cmd) {
        LambdaQueryWrapper<TokenUsage> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.isNotBlank(cmd.getUsageType())) {
            wrapper.eq(TokenUsage::getUsageType, cmd.getUsageType());
        }
        if (StringUtils.isNotBlank(cmd.getModelName())) {
            wrapper.eq(TokenUsage::getModelName, cmd.getModelName());
        }
        if (StringUtils.isNotBlank(cmd.getProvider())) {
            wrapper.eq(TokenUsage::getProvider, cmd.getProvider());
        }
        if (StringUtils.isNotBlank(cmd.getConversationCode())) {
            wrapper.eq(TokenUsage::getConversationCode, cmd.getConversationCode());
        }
        if (StringUtils.isNotBlank(cmd.getMessageCode())) {
            wrapper.eq(TokenUsage::getMessageCode, cmd.getMessageCode());
        }

        long cnt = tokenUsageDao.count(wrapper);
        if (cnt == 0) {
            return ImmutablePair.of(0L, List.of());
        }

        int pageNo = cmd.getPageNo() != null && cmd.getPageNo() > 0 ? cmd.getPageNo() : 1;
        int pageSize = cmd.getPageSize() != null && cmd.getPageSize() > 0 ? cmd.getPageSize() : 10;

        List<TokenUsage> list = tokenUsageDao.list(
                wrapper.last("limit %s,%s".formatted((pageNo - 1) * pageSize, pageSize))
                        .orderByDesc(TokenUsage::getId));

        // 收集 agentCode、conversationCode 和 messageCode，批量查询关联数据
        List<String> agentCodes = list.stream()
                .map(TokenUsage::getAgentCode)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();

        List<String> conversationCodes = list.stream()
                .map(TokenUsage::getConversationCode)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();

        List<String> messageCodes = list.stream()
                .map(TokenUsage::getMessageCode)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();

        // 批量查Agent
        Map<String, Agent> agentMap = CollectionUtils.isEmpty(agentCodes)
                ? Collections.emptyMap()
                : agentDao.list(Wrappers.<Agent>lambdaQuery().in(Agent::getCode, agentCodes))
                        .stream()
                        .collect(Collectors.toMap(Agent::getCode, Function.identity(), (a, b) -> a));

        // 批量查会话
        Map<String, Conversation> conversationMap = CollectionUtils.isEmpty(conversationCodes)
                ? Collections.emptyMap()
                : conversationDao.list(Wrappers.<Conversation>lambdaQuery().in(Conversation::getCode, conversationCodes))
                        .stream()
                        .collect(Collectors.toMap(Conversation::getCode, Function.identity(), (a, b) -> a));

        // 批量查消息
        Map<String, ChatMessage> messageMap = CollectionUtils.isEmpty(messageCodes)
                ? Collections.emptyMap()
                : chatMessageDao.list(Wrappers.<ChatMessage>lambdaQuery().in(ChatMessage::getCode, messageCodes))
                        .stream()
                        .collect(Collectors.toMap(ChatMessage::getCode, Function.identity(), (a, b) -> a));

        // 组装 DTO
        List<TokenUsageDto> dtos = list.stream().map(usage -> {
            TokenUsageDto dto = new TokenUsageDto();
            BeanUtils.copyProperties(usage, dto);

            // 填充所属Agent
            if (StringUtils.isNotBlank(usage.getAgentCode())) {
                Agent agent = agentMap.get(usage.getAgentCode());
                if (agent != null) {
                    AgentDto agentDto = new AgentDto();
                    BeanUtils.copyProperties(agent, agentDto);
                    dto.setAgent(agentDto);
                }
            }

            // 填充关联会话
            if (StringUtils.isNotBlank(usage.getConversationCode())) {
                Conversation conv = conversationMap.get(usage.getConversationCode());
                if (conv != null) {
                    ConversationDto convDto = new ConversationDto();
                    BeanUtils.copyProperties(conv, convDto);
                    convDto.setType(conv.getConversationType());
                    convDto.setTargetName(null); // 无需填充 targetName，Conversation 表无此字段
                    dto.setConversation(convDto);
                }
            }

            // 填充关联消息
            if (StringUtils.isNotBlank(usage.getMessageCode())) {
                ChatMessage msg = messageMap.get(usage.getMessageCode());
                if (msg != null) {
                    dto.setMessage(ChatMsgDto.builder()
                            .sendId(msg.getSenderCode())
                            .message(msg.getContent())
                            .messageType(msg.getMessageType())
                            .conversationCode(msg.getConversationCode())
                            .status(msg.getStatus())
                            .taskId(msg.getTaskId())
                            .build());
                }
            }

            return dto;
        }).toList();

        return ImmutablePair.of(cnt, dtos);
    }

    /**
     * 管理端 - 根据 code 查询Token用量详情（含关联的会话和消息信息）
     */
    public TokenUsageDto getByCode(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }

        TokenUsage usage = tokenUsageDao.getOne(
                Wrappers.<TokenUsage>lambdaQuery().eq(TokenUsage::getCode, code));
        if (usage == null) {
            return null;
        }

        TokenUsageDto dto = new TokenUsageDto();
        BeanUtils.copyProperties(usage, dto);

        // 查询所属Agent
        if (StringUtils.isNotBlank(usage.getAgentCode())) {
            Agent agent = agentDao.getByCode(usage.getAgentCode());
            if (agent != null) {
                AgentDto agentDto = new AgentDto();
                BeanUtils.copyProperties(agent, agentDto);
                dto.setAgent(agentDto);
            }
        }

        // 查询关联会话
        if (StringUtils.isNotBlank(usage.getConversationCode())) {
            Conversation conv = conversationDao.getOneByCode(usage.getConversationCode());
            if (conv != null) {
                ConversationDto convDto = new ConversationDto();
                BeanUtils.copyProperties(conv, convDto);
                convDto.setType(conv.getConversationType());
                dto.setConversation(convDto);
            }
        }

        // 查询关联消息
        if (StringUtils.isNotBlank(usage.getMessageCode())) {
            ChatMessage msg = chatMessageDao.getOne(
                    Wrappers.<ChatMessage>lambdaQuery().eq(ChatMessage::getCode, usage.getMessageCode()));
            if (msg != null) {
                dto.setMessage(ChatMsgDto.builder()
                        .sendId(msg.getSenderCode())
                        .message(msg.getContent())
                        .messageType(msg.getMessageType())
                        .conversationCode(msg.getConversationCode())
                        .status(msg.getStatus())
                        .taskId(msg.getTaskId())
                        .build());
            }
        }

        return dto;
    }
}

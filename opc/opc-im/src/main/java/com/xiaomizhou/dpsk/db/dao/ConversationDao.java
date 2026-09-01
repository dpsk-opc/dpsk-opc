package com.xiaomizhou.dpsk.db.dao;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.collect.Maps;
import com.xiaomizhou.dpsk.constant.ConversationType;
import com.xiaomizhou.dpsk.constant.FileRefType;
import com.xiaomizhou.dpsk.db.FileService;
import com.xiaomizhou.dpsk.db.WorkflowTemplateComponent;
import com.xiaomizhou.dpsk.db.chat.ChatProtocol;
import com.xiaomizhou.dpsk.db.dto.ConversationDto;
import com.xiaomizhou.dpsk.db.dto.FileRecordDto;
import com.xiaomizhou.dpsk.db.mapper.ConversationMapper;
import com.xiaomizhou.dpsk.db.model.*;
import com.xiaomizhou.dpsk.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/20 12:40
 * @description
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ConversationDao extends ServiceImpl<ConversationMapper, Conversation> {


    private final AgentDao agentDao;

    private final ChatMessageDao chatMessageDao;

    private final TokenUsageDao tokenUsageDao;

    private final ChatGroupDao chatGroupDao;

    private final FileService fileService;

    private final WorkflowTemplateDao workflowTemplateDao;


    public ImmutablePair<Long, List<ConversationDto>> page(int pageNo, int pageSize, Integer type, String name, String ownerCode) {


        LambdaQueryWrapper<Conversation> wrapper = Wrappers.<Conversation>lambdaQuery()
                .eq(StringUtils.isNotBlank(ownerCode), Conversation::getOwnerCode, ownerCode)
                .eq(Objects.nonNull(type), Conversation::getConversationType, type);

        long cnt = count(wrapper);

        if (0 == cnt) {
            return ImmutablePair.of(0L, List.of());
        }

        wrapper.orderByDesc(Conversation::getLastMessageTime);
        wrapper.last(" limit %d,%d".formatted((pageNo - 1) * pageSize, pageSize));

        List<Conversation> list = list(wrapper);


        List<String> agentCodes = list.stream().filter(conversation -> Objects.equals(conversation.getConversationType(), ConversationType.SINGLE.getCode())).map(Conversation::getTargetCode).collect(Collectors.toList());
        List<Agent> agents = CollectionUtils.isEmpty(agentCodes) ? List.of() : agentDao.list(Wrappers.<Agent>lambdaQuery().in(Agent::getCode, agentCodes));


        List<String> groupCodes = list.stream().filter(conversation -> conversation.getConversationType().equals(ConversationType.GROUP.getCode())).map(Conversation::getTargetCode).toList();
        List<ChatGroup> groups = CollectionUtils.isEmpty(groupCodes) ? List.of() : chatGroupDao.lambdaQuery().in(ChatGroup::getCode, groupCodes).list();

        List<String> templateCodes = list.stream().filter(conversation -> conversation.getConversationType().equals(ConversationType.WORKFLOW.getCode())).map(Conversation::getTargetCode).toList();
        List<WorkflowTemplateDO> templates = CollectionUtils.isEmpty(templateCodes) ? List.of() : workflowTemplateDao.lambdaQuery().in(WorkflowTemplateDO::getCode, templateCodes).list();

        return ImmutablePair.of(cnt, list.stream().map(record -> {

            ConversationDto dto = new ConversationDto();

            dto.setLastMessage(record.getLastMessageContent());
            dto.setLastMessageTime(record.getLastMessageTime());
            dto.setCode(record.getCode());
            dto.setTargetCode(record.getTargetCode());


            if (ConversationType.SINGLE.getCode().equals(record.getConversationType())) {
                Agent at = agents.stream().filter(agent -> agent.getCode().equals(record.getTargetCode())).findFirst().orElse(null);

                if (Objects.nonNull(at)) {
                    dto.setTargetName(at.getName());
                    dto.setTargetAvatar(at.getAvatar());
                    dto.setTargetType(at.getType());
                    dto.setModality(at.getModality());

                    if (StringUtils.isNotBlank(at.getLlmConfig())) {
                        HashMap map = JsonUtils.toObj(at.getLlmConfig(), HashMap.class);
                        dto.setModelName(MapUtils.getString(map, "modelName", ""));
                    }

                }
            }

            if (ConversationType.GROUP.getCode().equals(record.getConversationType())) {
                ChatGroup group = groups.stream().filter(gp -> gp.getCode().equals(record.getTargetCode())).findFirst().orElse(null);
                if (Objects.nonNull(group)) {
                    dto.setTargetAvatar(group.getAvatar());
                    dto.setTargetName(group.getName());
                }
            }

            if (ConversationType.WORKFLOW.getCode().equals(record.getConversationType())) {
                WorkflowTemplateDO template = templates.stream().filter(t -> t.getCode().equals(record.getTargetCode())).findFirst().orElse(null);
                if (Objects.nonNull(template)) {
                    dto.setTargetName(template.getName());
                    dto.setTargetAvatar(template.getAvatar());
                }
            }


            dto.setLastMessageTime(record.getLastMessageTime());
            dto.setType(record.getConversationType());
            dto.setIsTop(record.getIsTop());

            return dto;
        }).collect(Collectors.toList()));

    }


    /**
     * 获取会话信息
     *
     * @param sendId
     * @param receiveId
     * @param type
     * @return
     */
    public Conversation getOne(String sendId, String receiveId, Integer type) {

        if (StringUtils.isAnyBlank(sendId, receiveId) || Objects.isNull(type)) {
            return null;
        }

        Conversation one = getOne(Wrappers.<Conversation>lambdaQuery().eq(Conversation::getOwnerCode, sendId)
                .eq(Conversation::getTargetCode, receiveId)
                .eq(Conversation::getConversationType, type));

        if (Objects.nonNull(one)) {
            return one;
        }

        // 单聊场景：发送方和接收方互换，查询是否存在会话
        if (ConversationType.SINGLE.getCode().equals(type)) {
            return getOne(Wrappers.<Conversation>lambdaQuery().eq(Conversation::getOwnerCode, receiveId)
                    .eq(Conversation::getTargetCode, sendId)
                    .eq(Conversation::getConversationType, ConversationType.SINGLE.getCode()));
        }

        // 群聊会话只有只有一个
        if (ConversationType.WORKFLOW.getCode().equals(type)) {
            return getOne(Wrappers.<Conversation>lambdaQuery().eq(Conversation::getTargetCode, receiveId)
                    .eq(Conversation::getConversationType, ConversationType.WORKFLOW.getCode()).last(" limit 1"));
        }

        // 群聊会话只有只有一个
        return getOne(Wrappers.<Conversation>lambdaQuery().eq(Conversation::getTargetCode, receiveId)
                .eq(Conversation::getConversationType, ConversationType.GROUP.getCode()).last(" limit 1"));
    }

    public Conversation getOneByCode(String code) {
        return getOne(Wrappers.<Conversation>lambdaQuery().eq(Conversation::getCode, code));
    }

    /**
     * 更新最后一条消息
     *
     * @param code
     * @param message
     * @param sendCode
     * @return
     */
    public boolean last(String code, String lastMessageCode, String message, String sendCode) {

        if (StringUtils.isAnyBlank(code, sendCode)) {
            return false;
        }

        Conversation conversation = getOne(Wrappers.<Conversation>lambdaQuery().eq(Conversation::getCode, code).select(Conversation::getId));
        if (Objects.isNull(conversation)) {
            return false;
        }

        Conversation model = new Conversation();
        model.setId(conversation.getId());
        model.setLastMessageCode(lastMessageCode);
        model.setLastMessageContent(message);
        model.setLastSenderCode(sendCode);
        model.setLastMessageTime(new Date());
        model.setUpdateTime(new Date());

        return updateById(model);

    }


    /**
     * 分页查询聊天历史
     *
     * @param conversationCode 会话编码
     * @param pageNo           页码
     * @param pageSize         每页大小
     * @return 聊天协议列表和总数
     */
    public ImmutablePair<Long, List<ChatProtocol>> chatPage(String conversationCode, String taskId,int pageNo, int pageSize) {

        if (StringUtils.isBlank(conversationCode)) {
            return ImmutablePair.of(0L, List.of());
        }


        Conversation conversation = getOneByCode(conversationCode);
        if (Objects.isNull(conversation)) {
            return ImmutablePair.of(0L, List.of());
        }

//        String owner = conversation.getOwnerCode();
//        String target = conversation.getTargetCode();
//
//        Integer conversationType = conversation.getConversationType();

        // select * from t_chat_message where
        // 1. 查询消息总数
        LambdaQueryWrapper<ChatMessage> wrapper = Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getConversationCode, conversationCode)
                .eq(StringUtils.isNoneBlank(taskId), ChatMessage::getTaskId, taskId)
                .in(ChatMessage::getMessageType, List.of("USER", "AI"));

        long total = chatMessageDao.count(wrapper);

        if (total == 0) {
            return ImmutablePair.of(0L, List.of());
        }

        // 2. 分页查询消息列表
        wrapper.orderByDesc(ChatMessage::getCreateTime)
                .last(" LIMIT %d,%d".formatted((pageNo - 1) * pageSize, pageSize));

        List<ChatMessage> messages = chatMessageDao.list(wrapper);

        // 3. 收集发送者编码，批量查询 Agent 信息
        List<String> users = messages.stream().map(ChatMessage::getSenderCode).collect(Collectors.toList());
        Map<String, Agent> agentMap = CollectionUtils.isEmpty(users) ? Maps.newHashMap() : agentDao.list(
                        Wrappers.<Agent>lambdaQuery()
                                .in(Agent::getCode, users))
                .stream()
                .collect(Collectors.toMap(Agent::getCode, a -> a, (a1, a2) -> a1));

        // 4. 收集消息编码，批量查询 Token 使用数据
        List<String> messageCodes = messages.stream()
                .map(ChatMessage::getCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Map<String, TokenUsage> usageMap = CollectionUtils.isEmpty(messageCodes) ? Collections.emptyMap(): tokenUsageDao.list(
                        Wrappers.<TokenUsage>lambdaQuery()
                                .in(TokenUsage::getMessageCode, messageCodes))
                .stream()
                .collect(Collectors.toMap(TokenUsage::getMessageCode, u -> u, (u1, u2) -> u1));

        List<Long> quotedMessageCodes = messages.stream()
                .map(ChatMessage::getParentId)
                .filter(Objects::nonNull)
                .toList();

        Map<Long, ChatMessage> quotedMessageMap = CollectionUtils.isEmpty(quotedMessageCodes) ? Collections.emptyMap() : chatMessageDao.list(
                        Wrappers.<ChatMessage>lambdaQuery()
                                .in(ChatMessage::getId, quotedMessageCodes))
                .stream()
                .collect(Collectors.toMap(ChatMessage::getId, m -> m, (m1, m2) -> m1));

        Map<String, List<FileRecordDto>> files = fileService.getFileCodesByRefCode(messageCodes, FileRefType.CHAT_MESSAGE);

        // 5. 组装 ChatProtocol
        List<ChatProtocol> protocols = messages.stream().map(msg -> {
            Agent senderAgent = agentMap.get(msg.getSenderCode());

            // 构建用户信息
            ChatProtocol.User sender = null;
            if (Objects.nonNull(senderAgent)) {
                sender = new ChatProtocol.User(
                        senderAgent.getCode(),
                        senderAgent.getName(),
                        senderAgent.getAvatar(),
                        senderAgent.getNickname()
                );
            }



            // Token 使用情况
            TokenUsage usage = usageMap.get(msg.getCode());
            ChatProtocol.TokenUsage tokenUsage = null;
            if (Objects.nonNull(usage)) {
                tokenUsage = ChatProtocol.TokenUsage.builder()
                        .inputTokens(usage.getInputTokens() != null ? usage.getInputTokens() : 0)
                        .outputTokens(usage.getOutputTokens() != null ? usage.getOutputTokens() : 0)
                        .totalTokens(usage.getTotalTokens() != null ? usage.getTotalTokens() : 0)
                        .build();
            }

            // 构建消息内容
            ChatProtocol.Content content = new ChatProtocol.Content();
            content.setContent(msg.getContent());
            content.setEventType("message");
            content.setMsgCode(msg.getCode());
            content.setMsgStatus(msg.getStatus());
            content.setFiles(files.get(msg.getCode()));

            ChatProtocol.QuotedMessage quotedMessage = null;
            if (quotedMessageMap.containsKey(msg.getParentId())) {

                try {
                    ChatMessage cm = quotedMessageMap.get(msg.getParentId());
                    if (Objects.nonNull(cm) && Objects.nonNull(agentMap.get(cm.getCode()))) {
                        Agent agent = agentMap.get(cm.getCode());
                        quotedMessage = ChatProtocol.QuotedMessage.builder()
                                .content(quotedMessageMap.get(msg.getParentId()).getContent())
                                .senderName(agent.getName())
                                .nickname(agent.getNickname())
                                .msgCode(quotedMessageMap.get(msg.getParentId()).getCode())
                                .build();
                    }
                } catch (Exception e) {
                    log.error("Error building quoted message", e);
                }
            }


            return ChatProtocol.builder()
                    .finished(true)
                    .tokenUsage(tokenUsage)
                    .content(content)
                    .user(sender)
                    .createTime(msg.getCreateTime())
                    .quotedMessage(quotedMessage)
                    .build();
        }).collect(Collectors.toList());

        return ImmutablePair.of(total, protocols);
    }

    /**
     * 置顶
     *
     * @param conversationCode
     * @param top
     * @return
     */
    public boolean setTop(String conversationCode, Integer top) {

        if (StringUtils.isBlank(conversationCode)) {
            return false;
        }
        return update(Wrappers.<Conversation>lambdaUpdate().eq(Conversation::getCode, conversationCode).set(Conversation::getIsTop, top));
    }
}

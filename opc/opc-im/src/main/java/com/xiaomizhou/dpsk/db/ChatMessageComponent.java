package com.xiaomizhou.dpsk.db;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xiaomizhou.dpsk.constant.ConversationType;
import com.xiaomizhou.dpsk.utils.JsonUtils;
import com.xiaomizhou.dpsk.db.dao.*;
import com.xiaomizhou.dpsk.db.dto.ChatMsgDto;
import com.xiaomizhou.dpsk.db.model.ChatGroup;
import com.xiaomizhou.dpsk.db.model.ChatMessage;
import com.xiaomizhou.dpsk.db.model.Conversation;
import com.xiaomizhou.dpsk.utils.SequenceUtils;
import dev.langchain4j.model.output.TokenUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Objects;
import java.util.Optional;

import static com.xiaomizhou.dpsk.utils.SequenceUtils.UUIDSequenceGenerator.CHAT_MESSAGE_PREFIX;
import static com.xiaomizhou.dpsk.utils.SequenceUtils.UUIDSequenceGenerator.CONVERSATION_PREFIX;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/22 11:14
 * @description
 */
@RequiredArgsConstructor
@Slf4j
@Component
public class ChatMessageComponent {


    private final AgentComponent agentComponent;

    private final ChatGroupDao chatGroupDao;

    private final ConversationDao conversationDao;

    private final ChatMessageDao chatMessageDao;

    private final TokenUsageDao tokenUsageDao;


    /**
     * 获取会话code
     *
     * @param sendId
     * @param targetId
     * @return
     */
    public ImmutablePair<String, ConversationType> getConversationCode(String sendId, String targetId) {

        if (StringUtils.isAnyBlank(sendId, targetId)) {
            return ImmutablePair.nullPair();
        }

        Conversation con = conversationDao.getOne(Wrappers.<Conversation>lambdaQuery().and(w -> {
            w.and(w1 -> {
                w1.eq(Conversation::getOwnerCode, sendId);
                w1.eq(Conversation::getTargetCode, targetId);
            }).or(w2 -> {
                w2.eq(Conversation::getTargetCode, sendId);
                w2.eq(Conversation::getOwnerCode, targetId);
            });
        }).orderByDesc(Conversation::getId).last(" limit 1"));

        return Objects.isNull(con) ? ImmutablePair.nullPair() : ImmutablePair.of(con.getCode(), ConversationType.getByCode(con.getConversationType()));
    }


    /**
     *
     * @param code
     * @return
     */
    public ChatMessage getByCode(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }

        return chatMessageDao.getOne(Wrappers.<ChatMessage>lambdaQuery().eq(ChatMessage::getCode, code));
    }

    /**
     * 新建聊天消息
     *
     * @param sendId
     * @param dto
     * @param token
     * @param modelName
     * @return
     */
    public String newGroupChatMsg(String sendId, ChatMsgDto dto, TokenUsage token, String modelName) {

        if (StringUtils.isAnyBlank(sendId) || Objects.isNull(dto)) {
            return "";
        }


        final String targetId = dto.getTargetId();

        String msgCode = SequenceUtils.generator().next(CHAT_MESSAGE_PREFIX);

        // save the msg first
        ChatMessage msg = new ChatMessage();
        msg.setContent(dto.getMessage());
        msg.setMessageType(dto.getMessageType());
        msg.setConversationType(ConversationType.GROUP.name());
        msg.setSenderCode(sendId);
        msg.setReceiverCode(targetId);
        msg.setCode(msgCode);
        msg.setCreateTime(new Date());
        msg.setUpdateTime(new Date());
        msg.setStatus("SENT");
        msg.setContentType(0);

        if (StringUtils.isNotBlank(dto.getParentMsgCode())) {
            ChatMessage cm = getByCode(dto.getParentMsgCode());
            msg.setParentId(Objects.isNull(cm) ? 0L : cm.getId());
        }

        msg.setMentionedList(JsonUtils.toJson(dto.getMentionedList()));
        msg.setTaskId(dto.getTaskId());

        chatMessageDao.save(msg);


        // save or update the conversation
        String conversationCode = Optional.ofNullable(conversationDao.getOne(sendId, targetId, ConversationType.GROUP.getCode())).map(conversation -> {
            conversationDao.last(conversation.getCode(), msgCode, dto.getMessage(), sendId);
            return conversation.getCode();
        }).orElseGet(() -> {
            Conversation model = new Conversation();
            model.setOwnerCode(sendId);
            model.setTargetCode(targetId);
            model.setConversationType(ConversationType.GROUP.getCode());
            model.setLastMessageCode(msgCode);
            model.setLastMessageTime(new Date());
            model.setLastSenderCode(sendId);
            model.setLastMessageContent(dto.getMessage());
            model.setCreateTime(new Date());
            model.setUpdateTime(new Date());
            model.setCode(SequenceUtils.generator().next(CONVERSATION_PREFIX));
            conversationDao.save(model);

            return model.getCode();
        });

        if (Objects.isNull(token)) {
            return msgCode;
        }


        // save token
        com.xiaomizhou.dpsk.db.model.TokenUsage usage = new com.xiaomizhou.dpsk.db.model.TokenUsage();
        usage.setTotalTokens(token.totalTokenCount());
        usage.setInputTokens(token.inputTokenCount());
        usage.setOutputTokens(token.outputTokenCount());

        usage.setAgentCode(dto.getTargetId());
        usage.setCode(SequenceUtils.generator().next("TKU"));
        usage.setConversationCode(dto.getConversationCode());
        usage.setMessageCode(msg.getCode());
        usage.setModelName(modelName);
        usage.setCreateTime(new Date());
        usage.setUpdateTime(new Date());

        tokenUsageDao.save(usage);

        return msg.getCode();
    }


    /**
     * 新建聊天消息
     *
     * @param dto
     * @return
     */
    public String newSingleChatMsg(final String sendId, ChatMsgDto dto,TokenUsage token,String modelName) {

        if (Objects.isNull(dto) || StringUtils.isAnyBlank(sendId, dto.getTargetId())) {
            return "";
        }


        final String targetId = dto.getTargetId();

        String msgCode = SequenceUtils.generator().next(CHAT_MESSAGE_PREFIX);

        // save the msg first
        ChatMessage msg = new ChatMessage();
        msg.setContent(dto.getMessage());
        msg.setMessageType(dto.getMessageType());
        msg.setConversationType(ConversationType.SINGLE.name());
        msg.setSenderCode(sendId);
        msg.setReceiverCode(targetId);
        msg.setCode(msgCode);
        msg.setCreateTime(new Date());
        msg.setUpdateTime(new Date());
        msg.setStatus("SENT");
        msg.setContentType(0);

        if (StringUtils.isNotBlank(dto.getParentMsgCode())) {
            ChatMessage chat = getByCode(dto.getParentMsgCode());
            msg.setParentId(Objects.isNull(chat) ? 0L : chat.getId());
        }

        msg.setMentionedList(JsonUtils.toJson(dto.getMentionedList()));
        msg.setTaskId(dto.getTaskId());

        chatMessageDao.save(msg);


        // save or update the conversation
        String conversationCode = Optional.ofNullable(conversationDao.getOne(sendId, targetId, dto.getConversationType())).map(conversation -> {
            conversationDao.last(conversation.getCode(), msg.getCode(), dto.getMessage(), sendId);
            return conversation.getCode();
        }).orElseGet(() -> {
            Conversation model = new Conversation();
            model.setOwnerCode(sendId);
            model.setTargetCode(targetId);
            model.setConversationType(ConversationType.SINGLE.getCode());
            model.setLastMessageCode(msg.getCode());
            model.setLastMessageTime(new Date());
            model.setLastSenderCode(sendId);
            model.setLastMessageContent(dto.getMessage());
            model.setCreateTime(new Date());
            model.setUpdateTime(new Date());
            model.setCode(SequenceUtils.generator().next(CONVERSATION_PREFIX));
            conversationDao.save(model);

            return model.getCode();
        });

        if(Objects.isNull(token)) {
            return msgCode;
        }
        // save token
        com.xiaomizhou.dpsk.db.model.TokenUsage usage = new com.xiaomizhou.dpsk.db.model.TokenUsage();
        usage.setTotalTokens(token.totalTokenCount());
        usage.setInputTokens(token.inputTokenCount());
        usage.setOutputTokens(token.outputTokenCount());

        usage.setAgentCode(dto.getTargetId());
        usage.setCode(SequenceUtils.generator().next("TKU"));
        usage.setConversationCode(dto.getConversationCode());
        usage.setMessageCode(msg.getCode());
        usage.setModelName(modelName);
        usage.setCreateTime(new Date());
        usage.setUpdateTime(new Date());

        tokenUsageDao.save(usage);

        return msgCode;
    }
}

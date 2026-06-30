package com.xiaomizhou.dpsk.db;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xiaomizhou.dpsk.constant.ConversationType;
import com.xiaomizhou.dpsk.constant.FileRefType;
import com.xiaomizhou.dpsk.constant.MessageStatus;
import com.xiaomizhou.dpsk.db.dao.ChatMessageDao;
import com.xiaomizhou.dpsk.db.dao.ConversationDao;
import com.xiaomizhou.dpsk.db.dao.TokenUsageDao;
import com.xiaomizhou.dpsk.db.dto.ChatMsgDto;
import com.xiaomizhou.dpsk.db.model.ChatMessage;
import com.xiaomizhou.dpsk.db.model.Conversation;
import com.xiaomizhou.dpsk.utils.JsonUtils;
import com.xiaomizhou.dpsk.utils.SequenceUtils;
import dev.langchain4j.model.output.TokenUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

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

    private final FileService fileService;

    private final ConversationDao conversationDao;

    private final ChatMessageDao chatMessageDao;

    private final TokenUsageDao tokenUsageDao;

    private final AgentComponent agentComponent;

    private final WorkflowTaskComponent workflowTaskComponent;


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
     * 新建工作流消息
     *
     * @param sendId
     * @param dto
     * @param token
     * @param modelName
     * @return
     */
    public String newWorkflowMsg(String sendId, ChatMsgDto dto, TokenUsage token, String modelName) {
        if (Objects.isNull(dto) || StringUtils.isAnyBlank(sendId, dto.getTargetId())) {
            return "";
        }


        final String targetId = dto.getTargetId();

        String msgCode = SequenceUtils.generator().next(CHAT_MESSAGE_PREFIX);

        // save the msg first
        ChatMessage msg = new ChatMessage();
        msg.setContent(dto.getMessage());
        msg.setMessageType(dto.getMessageType());
        msg.setSenderCode(sendId);
        msg.setReceiverCode(targetId);
        msg.setCode(msgCode);
        msg.setCreateTime(new Date());
        msg.setUpdateTime(new Date());
        msg.setStatus(MessageStatus.SENT);
        msg.setContentType(0);
        msg.setTaskId(dto.getTaskId());
        msg.setConversationCode(dto.getConversationCode());

        if (StringUtils.isNotBlank(dto.getStatus())) {
            msg.setStatus(dto.getStatus());
        }

        if (StringUtils.isNotBlank(dto.getParentMsgCode())) {
            ChatMessage chat = getByCode(dto.getParentMsgCode());
            msg.setParentId(Objects.isNull(chat) ? 0L : chat.getId());
        }

        msg.setMentionedList(JsonUtils.toJson(dto.getMentionedList()));

        chatMessageDao.save(msg);

        boolean isUser = agentComponent.isUser(sendId);

        Conversation conv = conversationDao.getOneByCode(dto.getConversationCode());
        conversationDao.lambdaUpdate().set(Conversation::getLastMessageCode, msgCode)
                .set(Conversation::getLastMessageContent, dto.getMessage())
                .set(Conversation::getLastMessageTime, new Date())
                .set(isUser, Conversation::getLastUserMessageCode, msgCode)   // 非真实用户，填充关联用户消息编码
                .set(Conversation::getLastSenderCode, sendId)
                .set(Conversation::getUpdateTime, new Date())
                .eq(Conversation::getId, conv.getId()).update();

        // 非真实用户，填充关联用户消息编码
        if (!isUser) {
            LambdaUpdateWrapper<ChatMessage> wrapper = Wrappers.<ChatMessage>lambdaUpdate()
                    .set(ChatMessage::getRelateUserMessageCode, conv.getLastUserMessageCode())
                    .eq(ChatMessage::getCode, msgCode);
            chatMessageDao.update(null, wrapper);
        }

        // 更新消息编码
        List<String> fileCodes = dto.getFileCodes();
        if (CollectionUtils.isNotEmpty(fileCodes)) {
            fileService.updateRefCode(msgCode, FileRefType.CHAT_MESSAGE, fileCodes);
        }

        if (Objects.isNull(token)) {
            return msgCode;
        }
        // save token
        com.xiaomizhou.dpsk.db.model.TokenUsage usage = new com.xiaomizhou.dpsk.db.model.TokenUsage();
        usage.setTotalTokens(token.totalTokenCount());
        usage.setInputTokens(token.inputTokenCount());
        usage.setOutputTokens(token.outputTokenCount());

        usage.setAgentCode(dto.getSendId());
        usage.setCode(SequenceUtils.generator().next("TKU"));
        usage.setConversationCode(dto.getConversationCode());
        usage.setMessageCode(msg.getCode());
        usage.setModelName(modelName);
        usage.setTaskId(dto.getTaskId());
        usage.setCreateTime(new Date());
        usage.setUpdateTime(new Date());

        tokenUsageDao.save(usage);

        return msgCode;
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
        msg.setSenderCode(sendId);
        msg.setReceiverCode(targetId);
        msg.setCode(msgCode);
        msg.setCreateTime(new Date());
        msg.setUpdateTime(new Date());
        msg.setStatus(MessageStatus.SENT);
        msg.setContentType(0);

        if (StringUtils.isNotBlank(dto.getParentMsgCode())) {
            ChatMessage cm = getByCode(dto.getParentMsgCode());
            msg.setParentId(Objects.isNull(cm) ? 0L : cm.getId());
        }

        msg.setMentionedList(JsonUtils.toJson(dto.getMentionedList()));
        msg.setTaskId(dto.getTaskId());

        chatMessageDao.save(msg);

        boolean isUser = agentComponent.isUser(sendId);


        // save or update the conversation
        String conversationCode = Optional.ofNullable(conversationDao.getOne(sendId, targetId, ConversationType.GROUP.getCode())).map(conversation -> {
            // 非真实用户，填充关联用户消息编码
            LambdaUpdateWrapper<ChatMessage> wrapper = Wrappers.<ChatMessage>lambdaUpdate()
                    .set(ChatMessage::getConversationCode, conversation.getCode())
                    .eq(ChatMessage::getCode, msgCode);
            if (!isUser) {
                // 更新消息编码
                wrapper.set(ChatMessage::getRelateUserMessageCode, conversation.getLastUserMessageCode());
            }
            chatMessageDao.update(null, wrapper);

            conversationDao.last(conversation.getCode(), msgCode, dto.getMessage(), sendId);

            return conversation.getCode();
        }).orElseGet(() -> {

            String code = SequenceUtils.generator().next(CONVERSATION_PREFIX);

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
            model.setCode(code);
            model.setLastUserMessageCode(isUser ? msg.getCode() : null);
            conversationDao.save(model);

            // 更新消息编码
            chatMessageDao.update(null, Wrappers.<ChatMessage>lambdaUpdate().set(ChatMessage::getConversationCode, code).eq(ChatMessage::getCode, msg.getCode()));

            return model.getCode();
        });


        // 更新消息编码
        List<String> fileCodes = dto.getFileCodes();
        if (CollectionUtils.isNotEmpty(fileCodes)) {
            fileService.updateRefCode(msgCode, FileRefType.CHAT_MESSAGE, fileCodes);
        }

        if (Objects.isNull(token)) {
            return msgCode;
        }


        // save token
        com.xiaomizhou.dpsk.db.model.TokenUsage usage = new com.xiaomizhou.dpsk.db.model.TokenUsage();
        usage.setTotalTokens(token.totalTokenCount());
        usage.setInputTokens(token.inputTokenCount());
        usage.setOutputTokens(token.outputTokenCount());

        usage.setAgentCode(dto.getSendId());
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
    public String newSingleChatMsg(final String sendId, ChatMsgDto dto, TokenUsage token, String modelName) {

        if (Objects.isNull(dto) || StringUtils.isAnyBlank(sendId, dto.getTargetId())) {
            return "";
        }


        final String targetId = dto.getTargetId();

        String msgCode = SequenceUtils.generator().next(CHAT_MESSAGE_PREFIX);

        // save the msg first
        ChatMessage msg = new ChatMessage();
        msg.setContent(dto.getMessage());
        msg.setMessageType(dto.getMessageType());
        msg.setSenderCode(sendId);
        msg.setReceiverCode(targetId);
        msg.setCode(msgCode);
        msg.setCreateTime(new Date());
        msg.setUpdateTime(new Date());
        msg.setStatus(MessageStatus.SENT);
        msg.setContentType(0);

        if (StringUtils.isNotBlank(dto.getStatus())) {
            msg.setStatus(dto.getStatus());
        }

        if (StringUtils.isNotBlank(dto.getParentMsgCode())) {
            ChatMessage chat = getByCode(dto.getParentMsgCode());
            msg.setParentId(Objects.isNull(chat) ? 0L : chat.getId());
        }

        msg.setMentionedList(JsonUtils.toJson(dto.getMentionedList()));
        msg.setTaskId(dto.getTaskId());

        chatMessageDao.save(msg);

        boolean isUser = agentComponent.isUser(sendId);

        // save or update the conversation
        String conversationCode = Optional.ofNullable(conversationDao.getOne(sendId, targetId, dto.getConversationType())).map(conversation -> {

            // 非真实用户，填充关联用户消息编码
            LambdaUpdateWrapper<ChatMessage> wrapper = Wrappers.<ChatMessage>lambdaUpdate().set(ChatMessage::getConversationCode, conversation.getCode()).eq(ChatMessage::getCode, msgCode);
            if (!isUser) {
                // 更新消息编码
                wrapper.set(ChatMessage::getRelateUserMessageCode, conversation.getLastUserMessageCode());
            }
            chatMessageDao.update(null, wrapper);

            conversationDao.last(conversation.getCode(), msgCode, dto.getMessage(), sendId);

            return conversation.getCode();
        }).orElseGet(() -> {

            String code = SequenceUtils.generator().next(CONVERSATION_PREFIX);
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
            model.setCode(code);
            model.setLastUserMessageCode(isUser ? msg.getCode() : null);
            conversationDao.save(model);

            // 更新消息编码
            chatMessageDao.update(null, Wrappers.<ChatMessage>lambdaUpdate().set(ChatMessage::getConversationCode, code).eq(ChatMessage::getCode, msg.getCode()));

            return model.getCode();
        });


        // 更新消息编码
        List<String> fileCodes = dto.getFileCodes();
        if (CollectionUtils.isNotEmpty(fileCodes)) {
            fileService.updateRefCode(msgCode, FileRefType.CHAT_MESSAGE, fileCodes);
        }

        if (Objects.isNull(token)) {
            return msgCode;
        }
        // save token
        com.xiaomizhou.dpsk.db.model.TokenUsage usage = new com.xiaomizhou.dpsk.db.model.TokenUsage();
        usage.setTotalTokens(token.totalTokenCount());
        usage.setInputTokens(token.inputTokenCount());
        usage.setOutputTokens(token.outputTokenCount());

        usage.setAgentCode(dto.getSendId());
        usage.setCode(SequenceUtils.generator().next("TKU"));
        usage.setConversationCode(dto.getConversationCode());
        usage.setMessageCode(msg.getCode());
        usage.setModelName(modelName);
        usage.setCreateTime(new Date());
        usage.setUpdateTime(new Date());

        tokenUsageDao.save(usage);

        return msgCode;
    }

    /**
     * 将指定会话中所有消息标记为 IGNORE 状态，使其不再计入上下文。
     *
     * @param conversationCode 会话编码
     * @param msgCodes
     * @return 是否更新成功
     */
    public boolean ignore(String conversationCode, List<String> msgCodes) {
        if (StringUtils.isAnyBlank(conversationCode) || CollectionUtils.isEmpty(msgCodes)) {
            return false;
        }

        Conversation conversation = conversationDao.getOneByCode(conversationCode);
        if (conversation == null) {
            return false;
        }


        LambdaUpdateWrapper<ChatMessage> wrapper = Wrappers.<ChatMessage>lambdaUpdate()
                .set(ChatMessage::getStatus, MessageStatus.IGNORED)
                .set(ChatMessage::getUpdateTime, new Date())
                .in(ChatMessage::getCode, msgCodes);

        return chatMessageDao.update(wrapper);
    }

    /**
     * 统计指定用户作为接收方、状态为 SENT 的消息数量（未读消息数），按 conversation_code 分组。
     *
     * @param agentCode 登录用户编码
     * @return 按会话编码分组的未读消息数
     */
    public Map<String, Long> countSentMessagesByConversation(String agentCode) {
        if (StringUtils.isBlank(agentCode)) {
            return Collections.emptyMap();
        }
        List<ChatMessage> messages = chatMessageDao.list(Wrappers.<ChatMessage>lambdaQuery()
                .select(ChatMessage::getConversationCode)
                .eq(ChatMessage::getReceiverCode, agentCode)
                .eq(ChatMessage::getStatus, MessageStatus.SENT));
        return messages.stream()
                .filter(m -> StringUtils.isNotBlank(m.getConversationCode()))
                .collect(Collectors.groupingBy(ChatMessage::getConversationCode, Collectors.counting()));
    }

    /**
     * 将消息状态更新为 DELIVERED（已送达）。
     *
     * @param msgCodes 消息编码
     * @return 是否更新成功
     */
    public boolean delivered(List<String> msgCodes) {
        if (CollectionUtils.isEmpty(msgCodes)) {
            return false;
        }

        LambdaUpdateWrapper<ChatMessage> wrapper = Wrappers.<ChatMessage>lambdaUpdate()
                .set(ChatMessage::getStatus, MessageStatus.DELIVERED)
                .set(ChatMessage::getUpdateTime, new Date())
                .in(ChatMessage::getCode, msgCodes);

        return chatMessageDao.update(wrapper);
    }


    public boolean cancel(String msgCode) {
        if (StringUtils.isBlank(msgCode)) {
            return true;
        }

        while (true) {
            List<ChatMessage> list = chatMessageDao.list(Wrappers.<ChatMessage>lambdaQuery().eq(ChatMessage::getRelateUserMessageCode, msgCode)
                    .select(ChatMessage::getId)
                    .last("LIMIT 100"));

            if (CollectionUtils.isEmpty(list)) {
                break;
            }

            List<Long> ids = list.stream().map(ChatMessage::getId).toList();
            chatMessageDao.update(null, Wrappers.<ChatMessage>lambdaUpdate().set(ChatMessage::getStatus, MessageStatus.IGNORED).in(ChatMessage::getId, ids));
        }

        return true;
    }
}

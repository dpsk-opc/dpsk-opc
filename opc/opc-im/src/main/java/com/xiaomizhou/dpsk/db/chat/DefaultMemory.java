package com.xiaomizhou.dpsk.db.chat;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xiaomizhou.dpsk.db.AgentComponent;
import com.xiaomizhou.dpsk.db.dao.AgentDao;
import com.xiaomizhou.dpsk.db.dao.ChatMessageDao;
import com.xiaomizhou.dpsk.db.dao.ConversationDao;
import com.xiaomizhou.dpsk.db.dto.AgentDto;
import com.xiaomizhou.dpsk.db.model.Agent;
import com.xiaomizhou.dpsk.db.model.Conversation;
import com.xiaomizhou.dpsk.utils.AgentUtils;
import dev.langchain4j.data.message.*;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/20 20:09
 * @description
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DefaultMemory {

    private final ChatMessageDao chatMessageDao;

    private final ConversationDao conversationDao;

    private final AgentComponent agentComponent;

    public List<ChatMessage> getMessages(@NonNull String agentId, String conversationCode, int size) {

        AgentDto agent = agentComponent.getByCode(agentId);
        if (Objects.isNull(agent)) {
            return List.of();
        }


        String info = AgentUtils.toAgentDef(agent);

        List<ChatMessage> result = new ArrayList<>();
        result.add(SystemMessage.from(info));


        if (StringUtils.isBlank(conversationCode)) {
            return result;
        }

        Conversation conversation = conversationDao.getOneByCode(conversationCode);
        if (Objects.isNull(conversation)) {
            return result;
        }

        List<com.xiaomizhou.dpsk.db.model.ChatMessage> messages = chatMessageDao.list(Wrappers.<com.xiaomizhou.dpsk.db.model.ChatMessage>lambdaQuery()
                .and(w -> {
                    w.and(w1 -> {
                        w1.eq(com.xiaomizhou.dpsk.db.model.ChatMessage::getSenderCode, conversation.getOwnerCode());
                        w1.eq(com.xiaomizhou.dpsk.db.model.ChatMessage::getReceiverCode, conversation.getTargetCode());
                    }).or(w2 -> {
                        w2.eq(com.xiaomizhou.dpsk.db.model.ChatMessage::getSenderCode, conversation.getTargetCode());
                        w2.eq(com.xiaomizhou.dpsk.db.model.ChatMessage::getReceiverCode, conversation.getOwnerCode());
                    });
                })
                .orderByDesc(com.xiaomizhou.dpsk.db.model.ChatMessage::getId)
                .last(" limit %d".formatted(size)));

        if (CollectionUtils.isEmpty(messages)) {
            return result;
        }

        // reverse messages
        Collections.reverse(messages);

        List<ChatMessage> all = messages.stream().map(msg -> {
            if (Strings.CS.equals("default", msg.getSenderCode())) {
                return UserMessage.from(msg.getContent());
            }
            return AiMessage.from(msg.getContent());
        }).collect(Collectors.toList());
        result.addAll(all);

        return result;
    }

}

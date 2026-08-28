package com.xiaomizhou.dpsk.memory.impl;

import com.xiaomizhou.dpsk.db.ChatMessageComponent;
import com.xiaomizhou.dpsk.db.dao.ConversationDao;
import com.xiaomizhou.dpsk.db.model.ChatMessage;
import com.xiaomizhou.dpsk.db.model.Conversation;
import com.xiaomizhou.dpsk.memory.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Objects;

@RequiredArgsConstructor
@Slf4j
@Component
public class ConversationRepositoryImpl implements ConversationRepository {

    private final ConversationDao conversationDao;

    private final ChatMessageComponent chatMessageComponent;


    @Override
    public String getLastUserContent(String conversationCode) {
        if (StringUtils.isBlank(conversationCode)) {
            return " ";
        }

        Conversation conv = conversationDao.getOneByCode(conversationCode);
        if (Objects.isNull(conv) || StringUtils.isBlank(conv.getLastUserMessageCode())) {
            return " ";
        }

        ChatMessage msg = chatMessageComponent.getByCode(conv.getLastUserMessageCode());
        if (Objects.isNull(msg)) {
            return " ";
        }
        return msg.getContent();
    }
}

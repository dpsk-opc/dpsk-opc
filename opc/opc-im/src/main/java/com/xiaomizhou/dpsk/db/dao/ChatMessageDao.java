package com.xiaomizhou.dpsk.db.dao;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaomizhou.dpsk.db.mapper.MessageMapper;
import com.xiaomizhou.dpsk.db.model.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/18 18:22
 * @description
 */
@Component
@Slf4j
public class ChatMessageDao extends ServiceImpl<MessageMapper, ChatMessage> {
}

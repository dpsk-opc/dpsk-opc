package com.xiaomizhou.dpsk.db.dao;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaomizhou.dpsk.db.mapper.ChatGroupMemberMapper;
import com.xiaomizhou.dpsk.db.model.ChatGroupMember;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/18 18:33
 * @description
 */
@Component
@Slf4j
public class ChatGroupMemberDao extends ServiceImpl<ChatGroupMemberMapper, ChatGroupMember> {
}

package com.xiaomizhou.dpsk.db.chat;

import com.xiaomizhou.dpsk.db.ChatMessageComponent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/16 15:26
 * @description
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ChatService {

    private final AgentBridge agentBridge;

    private final ChatMessageComponent chatMessageComponent;

    /**
     * 聊天入口
     *
     * @param msgCode
     * @param userId  发送
     */
    public void doChat(String userId, String msgCode, List<String> mcpCodes, List<String> skillPaths) {

        if (StringUtils.isBlank(msgCode)) {
            return;
        }
        agentBridge.dispatch(userId, msgCode, mcpCodes, skillPaths);
    }

    /**
     * 取消指定消息对应的 Agent 会话。
     *
     * @param msgCode 用户发送的消息编码
     * @return true 表示成功设置取消标记
     */
    public boolean cancel(String msgCode) {
        agentBridge.cancel(msgCode);
        chatMessageComponent.cancel(msgCode);
        return true;
    }
}

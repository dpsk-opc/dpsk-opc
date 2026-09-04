package com.xiaomizhou.dpsk.memory.repository;

public interface ConversationRepository {


    String getLastUserContent(String conversationCode);

    /**
     * 获取会话置顶（pin）的消息编码。
     * <p>
     * pin 的消息通常为 AI 在长项目中沉淀出的通用知识/结论，需要每次构建 L0 窗口时都注入，
     * 即便它已不在最近窗口内。未 pin 返回 null。
     *
     * @param conversationCode 会话编码
     * @return pin 的消息编码，未 pin 或会话不存在返回 null
     */
    String getPinMsgCode(String conversationCode);

}

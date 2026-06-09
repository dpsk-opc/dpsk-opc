package com.xiaomizhou.dpsk.db.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/16 15:29
 * @description
 */
@Data
@Builder
public class ChatProtocol {

    /**
     * 创建一个完成的协议
     *
     * @param tokenUsage
     * @param content
     * @return
     */
    public static ChatProtocol finished(TokenUsage tokenUsage, User user, Content content) {
        ChatProtocol protocol = finished(user);
        protocol.setTokenUsage(tokenUsage);
        protocol.setContent(content);
        protocol.setCreateTime(new Date());
        return protocol;
    }

    public static ChatProtocol finished(User user) {
        return ChatProtocol.builder()
                .user(user)
                .finished(true)
                .createTime(new Date())
                .build();
    }




    public boolean finished;

    public TokenUsage tokenUsage;

    private Content content;

    private User user;

    private QuotedMessage quotedMessage;


    @Data
    @Builder
    public static class QuotedMessage {

        private String content;

        private String senderName;

        private String msgCode;
    }

    private Date createTime = new Date();

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Content {

        private String msgCode;

        private String eventType;

        private String content;

        public static Content thinking(String reasoning) {
            Content content = new Content();
            content.setContent(reasoning);
            content.setEventType("thinking");
            return content;
        }
    }

    @Data
    @AllArgsConstructor
    public static class User {

        private String userId;

        private String userName;

        private String avatar;
    }

    @Data
    @Builder
    public static class TokenUsage {

        private long inputTokens;

        private long outputTokens;

        private long totalTokens;

        @Override
        public String toString() {
            return "TokenUsage{" +
                    "inputTokens=" + inputTokens +
                    ", outputTokens=" + outputTokens +
                    ", totalTokens=" + totalTokens +
                    '}';
        }
    }

}

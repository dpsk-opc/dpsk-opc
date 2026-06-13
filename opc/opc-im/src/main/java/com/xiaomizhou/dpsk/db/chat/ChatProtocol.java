package com.xiaomizhou.dpsk.db.chat;

import com.xiaomizhou.dpsk.db.dto.FileRecordDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/16 15:29
 * @description
 */
@Data
@Builder
public class ChatProtocol {

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

        private String msgStatus;

        private List<FileRecordDto> files;
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

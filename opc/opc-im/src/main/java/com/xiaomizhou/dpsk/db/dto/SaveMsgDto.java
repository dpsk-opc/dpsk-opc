package com.xiaomizhou.dpsk.db.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SaveMsgDto {


    /**
     * 对话类型，单聊还是群聊
     */
    private Integer conversationType;

    /**
     * 会话编码
     */
    private String conversationCode;

    /**
     * 消息编码
     */
    private String msgCode;

}

package com.xiaomizhou.dpsk.db.dto;

import lombok.Data;

import java.util.Date;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/20 16:05
 * @description
 */
@Data
public class ConversationDto {


    private String code;


    private Integer type;


    private String targetName;


    private String targetCode;


    private String targetAvatar;


    /**
     * 模态: 0-TEXT, 1-IMAGE, 2-VIDEO, 3-AUDIO, 4-MIXED
     */
    private Integer modality;


    private String lastMessage;


    private Date lastMessageTime;

    private Integer isTop;

    /**
     * 会话置顶（pin）的消息：AI 沉淀的通用知识/结论，随每次 L0 窗口常驻。
     * 未 pin 时为 null。
     */
    private PinMsg pinMsg;

    /**
     * agent类型  see Agent#type
     */
    private String targetType;

    private String modelName;

    /**
     * 置顶（pin）消息摘要，供前端直接展示，无需二次查询消息表。
     */
    @Data
    public static class PinMsg {
        /** 置顶消息编码 */
        private String msgCode;
        /** 置顶消息内容 */
        private String content;
        /** 消息类型：USER / AI / TOOL 等 */
        private String messageType;
        /** 发送者编码 */
        private String senderCode;
    }

}

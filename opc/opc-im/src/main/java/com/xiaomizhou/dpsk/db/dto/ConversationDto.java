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
     * agent类型  see Agent#type
     */
    private String targetType;

    private String modelName;

}

package com.xiaomizhou.dpsk.controller.vo;

import lombok.Data;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/20 16:09
 * @description
 */
@Data
public class ConversationHttp {

    /**
     * 会话编码
     */
    private String conversationCode;

    /**
     * 名称（搜索用）
     */
    private String name;

    /**
     * 置顶
     */
    private Integer top;

    /**
     * 类型 0: 单聊 1: 群聊
     */
    private Integer type;

}

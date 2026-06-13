package com.xiaomizhou.dpsk.db.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 按会话分组统计的未读消息数量
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/12
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnreadCountDto {

    /**
     * 会话编码
     */
    private String conversationCode;

    /**
     * 该会话下的未读消息数
     */
    private Long count;
}

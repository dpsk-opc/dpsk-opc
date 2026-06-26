package com.xiaomizhou.dpsk.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/22 10:51
 * @description
 */
@AllArgsConstructor
@Getter
public enum ConversationType {

    SINGLE(0, "单聊"),

    GROUP(1, "群聊"),

    WORKFLOW(2, "专家团")
    ;

    private final Integer code;

    private final String description;


    public static ConversationType getByCode(Integer code) {
        for (ConversationType type : ConversationType.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }


}

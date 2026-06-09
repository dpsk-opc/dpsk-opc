package com.xiaomizhou.dpsk.core.model.request;

import lombok.Data;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/20 16:15
 * @description
 */
@Data
public class Order {

    private String field;

    private OrderType type;


    public enum OrderType {
        ASC, DESC
    }

}

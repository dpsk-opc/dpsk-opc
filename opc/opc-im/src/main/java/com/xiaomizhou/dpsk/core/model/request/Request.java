package com.xiaomizhou.dpsk.core.model.request;

import lombok.Data;

import java.util.List;
import java.util.Objects;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/20 16:13
 * @description
 */
@Data
public class Request<T> {

    private T param;

    private Page page;

    private List<Order> orders;

    public int pageNo() {
        return Objects.isNull(page) ? -1 : page.getPageNo();
    }

    public int pageSize() {
        return Objects.isNull(page) ? -1 : page.getPageSize();
    }
}

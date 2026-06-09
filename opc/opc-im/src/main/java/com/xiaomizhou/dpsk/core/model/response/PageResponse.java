package com.xiaomizhou.dpsk.core.model.response;

import lombok.Data;

import java.util.List;

/**
 * @author Administrator
 * @date 2026/5/13 21:46
 * @description
 */
@Data
public class PageResponse<T> {

    private int pageNo;

    private int pageSize;

    private long total;

    private List<T> list;
}

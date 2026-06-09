package com.xiaomizhou.dpsk.core.model.response;

import lombok.Data;

import java.util.List;

/**
 * @author Administrator
 * @date 2026/5/13 21:42
 * @description
 */
@Data
public class ListResponse<T> {

    public ListResponse(List<T> list) {
        this.list = list;
    }

    private List<T> list;

}

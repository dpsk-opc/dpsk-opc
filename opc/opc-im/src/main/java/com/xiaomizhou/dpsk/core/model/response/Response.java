package com.xiaomizhou.dpsk.core.model.response;

import lombok.Data;

/**
 * @author Administrator
 * @date 2026/5/13 21:32
 * @description
 */
@Data
public class Response<T> {


    private int code;

    private String msg;

    private T data;
}



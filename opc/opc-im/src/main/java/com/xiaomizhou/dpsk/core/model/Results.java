package com.xiaomizhou.dpsk.core.model;

import com.xiaomizhou.dpsk.core.model.response.ListResponse;
import com.xiaomizhou.dpsk.core.model.response.PageResponse;
import com.xiaomizhou.dpsk.core.model.response.Response;
import com.xiaomizhou.dpsk.core.exceptions.OpErrorCode;

import java.util.List;

/**
 * @author Administrator
 * @date 2026/5/13 21:37
 * @description
 */
public class Results {

    private Results() {
    }


    public static Response ok() {
        Response response = new Response();
        response.setMsg("ok");
        return response;
    }


    public static <T> Response<T> ok(T t) {
        Response<T> response = new Response<T>();
        response.setMsg("ok");
        response.setData(t);
        return response;
    }

    public static <T> Response<T> fail(int code, String msg) {
        Response<T> response = new Response<T>();
        response.setMsg(msg);
        response.setCode(code);
        return response;
    }

    public static <T> Response<T> fail(String msg) {
        return fail(OpErrorCode.INTERNAL_ERROR.getCode(), msg);
    }

    public static <T> Response<ListResponse<T>> list(List<T> list) {
        Response<ListResponse<T>> response = new Response<ListResponse<T>>();
        response.setMsg("ok");
        response.setData(new ListResponse<T>(list));
        return response;
    }

    public static <T> Response<PageResponse<T>> page(List<T> list, int pageNo, int pageSize, long total) {
        Response<PageResponse<T>> response = new Response<PageResponse<T>>();
        response.setMsg("ok");
        PageResponse<T> pageResponse = new PageResponse<T>();
        pageResponse.setList(list);
        pageResponse.setPageNo(pageNo);
        pageResponse.setPageSize(pageSize);
        pageResponse.setTotal(total);
        response.setData(pageResponse);
        return response;
    }

}

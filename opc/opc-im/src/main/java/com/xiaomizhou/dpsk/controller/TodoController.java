package com.xiaomizhou.dpsk.controller;

import com.xiaomizhou.dpsk.core.model.Results;
import com.xiaomizhou.dpsk.core.model.request.Request;
import com.xiaomizhou.dpsk.core.model.response.PageResponse;
import com.xiaomizhou.dpsk.core.model.response.Response;
import com.xiaomizhou.dpsk.db.TodoComponent;
import com.xiaomizhou.dpsk.db.dto.TodoCreateCmd;
import com.xiaomizhou.dpsk.db.dto.TodoItemDto;
import com.xiaomizhou.dpsk.db.dto.TodoUpdateCmd;
import com.xiaomizhou.dpsk.db.model.TodoItemDO;
import com.xiaomizhou.dpsk.utils.AuthContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 待办事项控制器。
 * <p>
 * 基准路径: POST /xiaomizhou/opc/v1/todo
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/24
 */
@RestController
@RequestMapping(value = "xiaomizhou/opc/v1/todo")
@Slf4j
@RequiredArgsConstructor
public class TodoController {

    private final TodoComponent todoComponent;

    /**
     * 分页查询待办列表。
     */
    @PostMapping(value = "list")
    public Response<PageResponse<TodoItemDto>> list(@RequestBody Request<Map<String, String>> request) {
        Map<String, String> param = request.getParam();
        String agentCode = param != null ? param.get("agentCode") : null;
        String status = param != null ? param.get("status") : null;
        String refType = param != null ? param.get("refType") : null;
        String refCode = param != null ? param.get("refCode") : null;

        int pageNo = request.pageNo() > 0 ? request.pageNo() : 1;
        int pageSize = request.pageSize() > 0 ? request.pageSize() : 10;

        ImmutablePair<Long, List<TodoItemDto>> pair = todoComponent.page(refCode, StringUtils.isNumeric(refType) ? Integer.parseInt(refType) : null, pageNo, pageSize, status != null ? Integer.parseInt(status) : null);
        return Results.page(pair.getRight(), pageNo, pageSize, pair.getLeft());
    }

    /**
     * 新建待办。
     */
    @PostMapping(value = "add")
    public Response<TodoItemDto> add(@Valid @RequestBody Request<TodoCreateCmd> request) {
        String ownerCode = AuthContext.getAgentCode();
        TodoItemDto dto = todoComponent.create(request.getParam(), ownerCode);
        return Results.ok(dto);
    }

    /**
     * 查询待办详情。
     */
    @PostMapping(value = "get")
    public Response<TodoItemDto> get(@RequestBody Request<String> request) {
        TodoItemDto dto = todoComponent.getByCode(request.getParam());
        if (dto == null) {
            return Results.fail("待办不存在");
        }
        return Results.ok(dto);
    }

    /**
     * 更新待办。
     */
    @PostMapping(value = "update")
    public Response<TodoItemDto> update(@Valid @RequestBody Request<TodoUpdateCmd> request) {
        TodoItemDto dto = todoComponent.update(request.getParam());
        return Results.ok(dto);
    }

    /**
     * 删除待办。
     */
    @PostMapping(value = "delete")
    public Response<Object> delete(@RequestBody Request<String> request) {
        todoComponent.delete(request.getParam());
        return Results.ok();
    }
}

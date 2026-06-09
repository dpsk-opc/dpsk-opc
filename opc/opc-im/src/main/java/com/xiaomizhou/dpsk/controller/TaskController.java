package com.xiaomizhou.dpsk.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xiaomizhou.dpsk.core.model.Results;
import com.xiaomizhou.dpsk.core.model.request.Request;
import com.xiaomizhou.dpsk.core.model.response.PageResponse;
import com.xiaomizhou.dpsk.core.model.response.Response;
import com.xiaomizhou.dpsk.db.TaskComponent;
import com.xiaomizhou.dpsk.db.dto.*;
import com.xiaomizhou.dpsk.task.model.TaskConsumeResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务控制器。
 *
 * @author eason - vipzhsh@163.com
 */
@RestController
@RequestMapping(value = "xiaomizhou/opc/v1/task")
@Slf4j
@RequiredArgsConstructor
public class TaskController {

    private final TaskComponent taskComponent;

    /**
     * 创建任务。
     */
    @PostMapping(value = "create")
    public Response<TaskDto> create(@Valid @RequestBody TaskCreateCmd cmd) {
        TaskDto dto = taskComponent.create(cmd);
        return Results.ok(dto);
    }

    /**
     * 更新任务。
     */
    @PostMapping(value = "update")
    public Response<TaskDto> update(@Valid @RequestBody TaskUpdateCmd cmd) {
        TaskDto dto = taskComponent.update(cmd);
        return Results.ok(dto);
    }

    /**
     * 删除任务（逻辑删除）。
     */
    @PostMapping(value = "delete")
    public Response<Object> delete(@RequestBody Request<String> request) {
        taskComponent.delete(request.getParam());
        return Results.ok();
    }

    /**
     * 根据编码查询任务。
     */
    @PostMapping(value = "get")
    public Response<TaskDto> get(@RequestBody Request<String> request) {
        TaskDto dto = taskComponent.getByCode(request.getParam());
        return Results.ok(dto);
    }

    /**
     * 分页查询任务。
     */
    @PostMapping(value = "page")
    public Response<PageResponse<TaskDto>> page(@RequestBody TaskQueryParam param) {
        IPage<TaskDto> page = taskComponent.queryPage(param);
        return Results.page(
                page.getRecords(),
                param.getPageNo(),
                param.getPageSize(),
                page.getTotal());
    }

    /**
     * 启用任务。
     */
    @PostMapping(value = "enable")
    public Response<Object> enable(@RequestBody Request<String> request) {
        taskComponent.enable(request.getParam());
        return Results.ok();
    }

    /**
     * 禁用任务。
     */
    @PostMapping(value = "disable")
    public Response<Object> disable(@RequestBody Request<String> request) {
        taskComponent.disable(request.getParam());
        return Results.ok();
    }

    /**
     * 手动触发任务。
     */
    @PostMapping(value = "trigger")
    public Response<TaskConsumeResult> trigger(@RequestBody TaskTriggerParam param) {
        TaskConsumeResult result = taskComponent.trigger(param);
        return Results.ok(result);
    }

    /**
     * 分页查询执行日志。
     */
    @PostMapping(value = "execution-log/page")
    public Response<PageResponse<TaskExecutionLogDto>> executionLogPage(@RequestBody TaskExecutionLogQueryParam param) {
        IPage<TaskExecutionLogDto> page = taskComponent.queryExecutionLogPage(param);
        return Results.page(
                page.getRecords(),
                param.getPageNo(),
                param.getPageSize(),
                page.getTotal());
    }
}

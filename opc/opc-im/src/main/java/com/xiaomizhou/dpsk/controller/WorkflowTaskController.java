package com.xiaomizhou.dpsk.controller;

import com.xiaomizhou.dpsk.core.model.Results;
import com.xiaomizhou.dpsk.core.model.request.Request;
import com.xiaomizhou.dpsk.core.model.response.PageResponse;
import com.xiaomizhou.dpsk.core.model.response.Response;
import com.xiaomizhou.dpsk.db.WorkflowTaskComponent;
import com.xiaomizhou.dpsk.db.dto.WorkflowTaskDto;
import com.xiaomizhou.dpsk.db.dto.WorkflowTaskQueryParam;
import com.xiaomizhou.dpsk.utils.AuthContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工作流任务控制器。
 * <p>
 * 基准路径: POST /xiaomizhou/opc/v1/workflow/task
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/25
 */
@RestController
@RequestMapping(value = "xiaomizhou/opc/v1/workflow/task")
@Slf4j
@RequiredArgsConstructor
public class WorkflowTaskController {

    private final WorkflowTaskComponent taskComponent;


    /**
     * 添加任务。
     * @param param
     * @return
     */
    @PostMapping(value = "add")
    public Response<WorkflowTaskDto> add(@RequestBody Request<WorkflowTaskDto> param){
        WorkflowTaskDto task = param.getParam();
        task.setOwnerCode(AuthContext.getAgentCode());
        WorkflowTaskDto result = taskComponent.add(task);
        return Results.ok(result);
    }

    /**
     * 分页查询任务列表。
     */
    @PostMapping(value = "list")
    public Response<PageResponse<WorkflowTaskDto>> list(@RequestBody Request<WorkflowTaskQueryParam> param) {
        ImmutablePair<Long, List<WorkflowTaskDto>> pair = taskComponent.page(param.getParam(), param.getPage().getPageNo(), param.getPage().getPageSize());
        return Results.page(pair.getRight(), param.getPage().getPageNo(), param.getPage().getPageSize(), pair.getLeft());
    }

    /**
     * 查询任务详情。
     */
    @PostMapping(value = "get")
    public Response<WorkflowTaskDto> get(@RequestBody Request<String> request) {
        WorkflowTaskDto dto = taskComponent.getByCode(request.getParam());
        if (dto == null) {
            return Results.fail("任务不存在");
        }
        return Results.ok(dto);
    }

    /**
     * 取消任务。
     */
    @PostMapping(value = "cancel")
    public Response<Object> cancel(@RequestBody Request<String> request) {
        taskComponent.cancel(request.getParam());
        return Results.ok();
    }

    /**
     * 删除任务。
     */
    @PostMapping(value = "delete")
    public Response<Object> delete(@RequestBody Request<String> request) {
        taskComponent.delete(request.getParam());
        return Results.ok();
    }
}

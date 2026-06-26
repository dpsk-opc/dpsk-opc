package com.xiaomizhou.dpsk.controller;

import com.xiaomizhou.dpsk.core.model.Results;
import com.xiaomizhou.dpsk.core.model.request.Request;
import com.xiaomizhou.dpsk.core.model.response.PageResponse;
import com.xiaomizhou.dpsk.core.model.response.Response;
import com.xiaomizhou.dpsk.db.WorkflowTemplateComponent;
import com.xiaomizhou.dpsk.db.dto.WorkflowTemplateCreateCmd;
import com.xiaomizhou.dpsk.db.dto.WorkflowTemplateDto;
import com.xiaomizhou.dpsk.db.dto.WorkflowTemplateQueryParam;
import com.xiaomizhou.dpsk.db.dto.WorkflowTemplateUpdateCmd;
import com.xiaomizhou.dpsk.utils.AuthContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 工作流模板控制器。
 * <p>
 * 基准路径: POST /xiaomizhou/opc/v1/workflow/template
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/25
 */
@RestController
@RequestMapping(value = "xiaomizhou/opc/v1/workflow/template")
@Slf4j
@RequiredArgsConstructor
public class WorkflowTemplateController {

    private final WorkflowTemplateComponent templateComponent;

    /**
     * 分页查询模板列表。
     */
    @PostMapping(value = "list")
    public Response<PageResponse<WorkflowTemplateDto>> list(@RequestBody Request<WorkflowTemplateQueryParam> param) {
        ImmutablePair<Long, List<WorkflowTemplateDto>> pair = templateComponent.page(param.getParam(),param.getPage().getPageNo(), param.getPage().getPageSize());
        return Results.page(pair.getRight(), param.getPage().getPageNo(), param.getPage().getPageSize(), pair.getLeft());
    }

    /**
     * 新建模板。
     */
    @PostMapping(value = "add")
    public Response<WorkflowTemplateDto> add(@Valid @RequestBody Request<WorkflowTemplateCreateCmd> request) {
        String ownerCode = AuthContext.getAgentCode();
        WorkflowTemplateDto dto = templateComponent.create(request.getParam(), ownerCode);
        return Results.ok(dto);
    }

    /**
     * 查询模板详情。
     */
    @PostMapping(value = "get")
    public Response<WorkflowTemplateDto> get(@RequestBody Request<String> request) {
        WorkflowTemplateDto dto = templateComponent.getByCode(request.getParam());
        if (dto == null) {
            return Results.fail("模板不存在");
        }
        return Results.ok(dto);
    }

    /**
     * 更新模板。
     */
    @PostMapping(value = "update")
    public Response<WorkflowTemplateDto> update(@Valid @RequestBody Request<WorkflowTemplateUpdateCmd> request) {
        WorkflowTemplateDto dto = templateComponent.update(request.getParam());
        return Results.ok(dto);
    }

    /**
     * 删除模板。
     */
    @PostMapping(value = "delete")
    public Response<Object> delete(@RequestBody Request<String> request) {
        templateComponent.delete(request.getParam());
        return Results.ok();
    }

    /**
     * 获取模板分类枚举列表（供前端 Tab 展示）。
     */
    @PostMapping(value = "categories")
    public Response<List<Map<String, Object>>> categories() {
        return Results.ok(templateComponent.getCategories());
    }
}

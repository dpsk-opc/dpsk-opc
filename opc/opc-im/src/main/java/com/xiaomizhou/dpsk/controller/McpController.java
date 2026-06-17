package com.xiaomizhou.dpsk.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xiaomizhou.dpsk.core.exceptions.BusinessException;
import com.xiaomizhou.dpsk.core.model.Results;
import com.xiaomizhou.dpsk.core.model.request.Request;
import com.xiaomizhou.dpsk.core.model.response.PageResponse;
import com.xiaomizhou.dpsk.core.model.response.Response;
import com.xiaomizhou.dpsk.core.ws.payload.McpResultPayload;
import com.xiaomizhou.dpsk.db.McpComponent;
import com.xiaomizhou.dpsk.db.dto.*;
import com.xiaomizhou.dpsk.tool.executor.McpElectronBridgeImpl;
import com.xiaomizhou.dpsk.utils.AuthContext;
import com.xiaomizhou.dpsk.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

/**
 * MCP 管理控制器。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/16
 */
@RestController
@RequestMapping(value = "xiaomizhou/opc/v1/mcp")
@Slf4j
@RequiredArgsConstructor
public class McpController {

    private final McpComponent mcpComponent;

    // ========== 工具发现 ==========

    @PostMapping(value = "discover-tools")
    public Response<McpDiscoverVO> discoverTools(@RequestBody Request<McpDiscoverRequest> request) {
        McpDiscoverRequest req = request != null ? request.getParam() : null;
        if (req == null) {
            req = new McpDiscoverRequest();
        }
        McpDiscoverVO result = mcpComponent.discoverTools(req);
        return Results.ok(result);
    }

    // ========== 模板管理 ==========

    @PostMapping(value = "template/list")
    public Response<PageResponse<McpTemplateVO>> templateList(@RequestBody Request<Map<String, Object>> request) {
        int pageNo = 1, pageSize = 20;
        String keyword = null;

        if (request != null) {
            if (request.getPage() != null) {
                pageNo = request.getPage().getPageNo() > 0 ? request.getPage().getPageNo() : 1;
                pageSize = request.getPage().getPageSize() > 0 ? request.getPage().getPageSize() : 20;
            }
            if (request.getParam() != null && request.getParam().get("keyword") != null) {
                keyword = String.valueOf(request.getParam().get("keyword"));
            }
        }

        IPage<McpTemplateVO> result = mcpComponent.listTemplates(pageNo, pageSize, keyword);
        return Results.page(result.getRecords(), pageNo, pageSize, result.getTotal());
    }

    @PostMapping(value = "template/add")
    public Response<Map<String, String>> templateAdd(@RequestBody Request<McpTemplateSaveRequest> request) {
        McpTemplateSaveRequest req = request != null ? request.getParam() : null;
        if (req == null) {
            req = new McpTemplateSaveRequest();
        }
        String id = mcpComponent.addTemplate(req);
        return Results.ok(Map.of("id", id));
    }

    @PostMapping(value = "template/update")
    public Response<Object> templateUpdate(@RequestBody Request<McpTemplateSaveRequest> request) {
        McpTemplateSaveRequest req = request != null ? request.getParam() : null;
        if (req == null) {
            req = new McpTemplateSaveRequest();
        }
        mcpComponent.updateTemplate(req);
        return Results.ok();
    }

    @PostMapping(value = "template/delete")
    public Response<Object> templateDelete(@RequestBody Request<McpDeleteRequest> request) {
        McpDeleteRequest req = request != null ? request.getParam() : null;
        String id = req != null ? req.getId() : null;
        mcpComponent.deleteTemplate(id);
        return Results.ok();
    }

    // ========== 绑定管理 ==========

    @PostMapping(value = "binding/list")
    public Response<PageResponse<McpBindingVO>> bindingList(@RequestBody Request<Map<String, Object>> request) {
        int pageNo = 1, pageSize = 20;
        String agentCode = null;
        Boolean enabled = null;

        if (request != null) {
            if (request.getPage() != null) {
                pageNo = request.getPage().getPageNo() > 0 ? request.getPage().getPageNo() : 1;
                pageSize = request.getPage().getPageSize() > 0 ? request.getPage().getPageSize() : 20;
            }
            if (request.getParam() != null) {
                Map<String, Object> param = request.getParam();
                if (param.get("agentCode") != null) {
                    agentCode = String.valueOf(param.get("agentCode"));
                }
                if (param.get("enabled") != null) {
                    enabled = Boolean.valueOf(String.valueOf(param.get("enabled")));
                }
            }
        }

        // 默认使用当前 Agent
        if (agentCode == null) {
            agentCode = AuthContext.getAgentCode();
        }

        IPage<McpBindingVO> result = mcpComponent.listBindings(pageNo, pageSize, agentCode, enabled);
        return Results.page(result.getRecords(), pageNo, pageSize, result.getTotal());
    }

    @PostMapping(value = "binding/save")
    public Response<Object> bindingSave(@RequestBody Request<McpBindingSaveRequest> request) {
        McpBindingSaveRequest req = request != null ? request.getParam() : null;
        if (req == null) {
            req = new McpBindingSaveRequest();
        }
        mcpComponent.saveBinding(req, req.getAgentCode());
        return Results.ok();
    }

    @PostMapping(value = "binding/delete")
    public Response<Object> bindingDelete(@RequestBody Request<McpDeleteRequest> request) {
        McpDeleteRequest req = request != null ? request.getParam() : null;
        String templateId = req != null ? req.getTemplateId() : null;

        if(Objects.isNull(req)){
            throw BusinessException.paramError("binding/delete 参数异常!");
        }

        mcpComponent.deleteBinding(templateId, req.getAgentCode());
        return Results.ok();
    }

    // ========== Electron 回调 ==========

    /**
     * 前端 Electron 通过 HTTP 回传 MCP 工具调用结果，替代 WebSocket 回调。
     */
    @PostMapping(value = "mcp-call-result")
    public Response<Object> mcpCallResult(@RequestBody Request<McpResultPayload> resquest) {

        if (resquest == null) {
            throw BusinessException.paramError("mcp-call-result 请求参数为空!");
        }

        McpResultPayload payload = resquest.getParam();
        if(Objects.isNull(payload)){
            throw BusinessException.paramError("mcp-call-result 参数异常!");
        }

        log.info("MCP call result received via HTTP: callId={}, success={}", payload.getCallId(), payload.isSuccess());

        // tools/list 结果通过 tools 字段返回，tools/call 结果通过 result 字段返回
        String resultStr;
        if (payload.getTools() != null) {
            resultStr = JsonUtils.toJson(payload.getTools());
        } else {
            resultStr = payload.getResult();
        }

        McpElectronBridgeImpl.complete(payload.getCallId(), payload.isSuccess(),
                resultStr, payload.getError());
        return Results.ok();
    }
}

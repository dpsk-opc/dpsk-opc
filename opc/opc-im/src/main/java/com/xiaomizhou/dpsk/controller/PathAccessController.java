package com.xiaomizhou.dpsk.controller;

import com.xiaomizhou.dpsk.core.model.Results;
import com.xiaomizhou.dpsk.core.model.request.Request;
import com.xiaomizhou.dpsk.core.model.response.Response;
import com.xiaomizhou.dpsk.tool.workspace.PathAccessConfirmManager;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作空间外路径访问确认控制器。
 * <p>
 * 前端收到 {@code path_access_confirm} 事件、用户做出选择后，调用本接口回传决定，
 * 唤醒正在阻塞等待的工具执行流程。
 * <p>
 * 基准路径: POST /xiaomizhou/opc/v1/path/access
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/9/20
 */
@RestController
@RequestMapping(value = "xiaomizhou/opc/v1/path/access")
@Slf4j
@RequiredArgsConstructor
public class PathAccessController {

    private final PathAccessConfirmManager confirmManager;

    /**
     * 提交用户对越界路径访问的决定。
     * <p>
     * decision 取值：ONCE（仅本次允许）/ SESSION_DIR（本次会话允许该目录）/ DENY（拒绝）
     */
    @PostMapping(value = "answer")
    public Response<Boolean> answer(@RequestBody Request<PathAccessAnswerDto> param) {
        PathAccessAnswerDto dto = param.getParam();
        if (dto == null || dto.getRequestId() == null) {
            return Results.fail("requestId不能为空");
        }
        boolean ok = confirmManager.submitDecision(dto.getRequestId(), dto.getDecision());
        if (!ok) {
            log.warn("answer ignored: no pending path access request for requestId={}", dto.getRequestId());
        }
        return Results.ok(ok);
    }

    /**
     * 用户主动取消（等同拒绝）。
     */
    @PostMapping(value = "cancel")
    public Response<Boolean> cancel(@RequestBody Request<String> param) {
        if (param.getParam() == null) {
            return Results.fail("requestId不能为空");
        }
        return Results.ok(confirmManager.cancelByRequest(param.getParam()));
    }

    @Data
    public static class PathAccessAnswerDto {
        /** 确认请求 ID */
        private String requestId;
        /** 用户决定：ONCE / SESSION_DIR / DENY */
        private String decision;
    }
}

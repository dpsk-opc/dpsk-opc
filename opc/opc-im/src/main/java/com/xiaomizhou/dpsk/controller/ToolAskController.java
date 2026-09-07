package com.xiaomizhou.dpsk.controller;

import com.xiaomizhou.dpsk.core.model.Results;
import com.xiaomizhou.dpsk.core.model.request.Request;
import com.xiaomizhou.dpsk.core.model.response.Response;
import com.xiaomizhou.dpsk.tool.ask.ToolAskManager;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工具询问用户控制器。
 * <p>
 * 前端监听到 tool_ask 事件、用户输入答案后，调用本接口把答案回传给正在等待的 ask_user 工具。
 * <p>
 * 基准路径: POST /xiaomizhou/opc/v1/tool/ask
 *
 * @author eason - vipzhsh@163.com
 */
@RestController
@RequestMapping(value = "xiaomizhou/opc/v1/tool/ask")
@Slf4j
@RequiredArgsConstructor
public class ToolAskController {

    private final ToolAskManager askManager;

    /**
     * 提交用户对 ask_user 问题的答案，唤醒阻塞等待的工具。
     */
    @PostMapping(value = "answer")
    public Response<Boolean> answer(@RequestBody Request<ToolAnswerDto> param) {
        ToolAnswerDto dto = param.getParam();
        if (dto == null || dto.getRequestId() == null) {
            return Results.fail("requestId不能为空");
        }
        boolean ok = askManager.submitAnswer(dto.getRequestId(), dto.getAnswer());
        if (!ok) {
            log.warn("answer ignored: no pending ask request for requestId={}", dto.getRequestId());
        }
        return Results.ok(ok);
    }

    /**
     * 用户主动取消一次提问（可选，与 Agent 取消解耦）。
     */
    @PostMapping(value = "cancel")
    public Response<Boolean> cancel(@RequestBody Request<String> param) {
        if (param.getParam() == null) {
            return Results.fail("requestId不能为空");
        }
        askManager.cancelByRequest(param.getParam());
        return Results.ok(true);
    }

    @Data
    public static class ToolAnswerDto {
        /** ask_user 工具生成的唯一请求 ID */
        private String requestId;
        /** 用户输入的答案 */
        private String answer;
    }
}

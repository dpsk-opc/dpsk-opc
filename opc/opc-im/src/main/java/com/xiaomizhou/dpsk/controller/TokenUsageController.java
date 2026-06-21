package com.xiaomizhou.dpsk.controller;

import com.xiaomizhou.dpsk.core.model.Results;
import com.xiaomizhou.dpsk.core.model.request.Request;
import com.xiaomizhou.dpsk.core.model.response.PageResponse;
import com.xiaomizhou.dpsk.core.model.response.Response;
import com.xiaomizhou.dpsk.db.TokenUsageComponent;
import com.xiaomizhou.dpsk.db.dto.TokenUsageDto;
import com.xiaomizhou.dpsk.db.dto.TokenUsagePageCmd;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Token用量管理控制器
 *
 * @author eason - vipzhsh@163.com
 */
@RestController
@RequestMapping(value = "xiaomizhou/opc/v1/admin/token-usage")
@Slf4j
@RequiredArgsConstructor
public class TokenUsageController {

    private final TokenUsageComponent tokenUsageComponent;

    /**
     * 管理端 - 分页查询Token用量列表
     */
    @PostMapping(value = "page")
    public Response<PageResponse<TokenUsageDto>> page(@RequestBody TokenUsagePageCmd cmd) {
        ImmutablePair<Long, List<TokenUsageDto>> pair = tokenUsageComponent.page(cmd);
        return Results.page(
                pair.getRight(),
                cmd.getPageNo(),
                cmd.getPageSize(),
                pair.getLeft());
    }

    /**
     * 管理端 - 根据 code 查询Token用量详情
     */
    @PostMapping(value = "detail")
    public Response<TokenUsageDto> detail(@RequestBody Request<String> request) {
        TokenUsageDto dto = tokenUsageComponent.getByCode(request.getParam());
        if (dto == null) {
            return Results.fail(404, "Token用量记录不存在");
        }
        return Results.ok(dto);
    }
}

package com.xiaomizhou.dpsk.controller;

import com.xiaomizhou.dpsk.constant.ConversationType;
import com.xiaomizhou.dpsk.controller.vo.ConversationHttp;
import com.xiaomizhou.dpsk.db.ChatMessageComponent;
import com.xiaomizhou.dpsk.db.chat.ChatProtocol;
import com.xiaomizhou.dpsk.db.chat.ChatService;
import com.xiaomizhou.dpsk.db.dao.ConversationDao;
import com.xiaomizhou.dpsk.db.dto.ChatMsgDto;
import com.xiaomizhou.dpsk.db.dto.ConversationDto;
import com.xiaomizhou.dpsk.core.model.Results;
import com.xiaomizhou.dpsk.core.model.request.Request;
import com.xiaomizhou.dpsk.core.model.response.PageResponse;
import com.xiaomizhou.dpsk.core.model.response.Response;
import com.xiaomizhou.dpsk.core.exceptions.BusinessException;
import com.xiaomizhou.dpsk.utils.AuthContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.Executors;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/20 15:56
 * @description
 */
@RestController
@RequestMapping(value = "xiaomizhou/opc/v1/conversation")
@Slf4j
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationDao conversationDao;

    private final ChatMessageComponent chatMessageComponent;

    private final ChatService chatService;

    @PostMapping(value = "list")
    public Response<PageResponse<ConversationDto>> list(@RequestBody Request<ConversationHttp> request) {

        ConversationHttp param = request.getParam();
        if (param == null) {
            param = new ConversationHttp();
        }

        ImmutablePair<Long, List<ConversationDto>> conversations = conversationDao.page(request.pageNo(), request.pageSize(), param.getType(), param.getName());
        return Results.page(conversations.right, request.pageNo(), request.pageSize(), conversations.left);
    }

    @PostMapping(value = "chat/list")
    public Response<PageResponse<ChatProtocol>> chats(@RequestBody Request<ConversationHttp> request) {
        ConversationHttp param = request.getParam() != null ? request.getParam() : new ConversationHttp();
        int pageNo = request.pageNo() > 0 ? request.pageNo() : 1;
        int pageSize = request.pageSize() > 0 ? request.pageSize() : 20;

        ImmutablePair<Long, List<ChatProtocol>> result = conversationDao.chatPage(param.getConversationCode(), pageNo, pageSize);
        return Results.page(result.right, pageNo, pageSize, result.left);
    }

    @PostMapping(value = "setTop")
    public Response<Boolean> setTop(@RequestBody Request<ConversationHttp> request) {
        ConversationHttp param = request.getParam() != null ? request.getParam() : new ConversationHttp();
        return Results.ok(conversationDao.setTop(param.getConversationCode(), param.getTop()));
    }

    /**
     * 保存消息
     *
     * @param request
     * @return
     */
    @PostMapping(value = "chat/save")
    public Response<String> addChat(@RequestBody Request<ChatMsgDto> request) {
        ChatMsgDto dto = request.getParam();
        if (dto == null) {
            return Results.fail("参数不能为空");
        }

        String sendCode = AuthContext.getAgentCode();

        Integer type = dto.getConversationType();


        if (ConversationType.SINGLE.getCode().equals(type)) {
            dto.setMessageType("USER");
            String code = chatMessageComponent.newSingleChatMsg(sendCode, dto, null, "");
            chatService.doChat(sendCode, code);
            return Results.ok(code);
        } else if (ConversationType.GROUP.getCode().equals(type)) {
            dto.setMessageType("USER");
            String code = chatMessageComponent.newGroupChatMsg(sendCode, dto, null, null);
            Executors.newSingleThreadExecutor().submit(() -> {
                chatService.doChat(sendCode, code);
            });
            return Results.ok(code);
        } else {
            throw BusinessException.paramError("不支持的会话类型: " + type);
        }
    }

}

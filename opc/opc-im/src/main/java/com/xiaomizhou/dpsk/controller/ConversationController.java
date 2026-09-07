package com.xiaomizhou.dpsk.controller;

import com.xiaomizhou.dpsk.agent.AgentBuildSpec;
import com.xiaomizhou.dpsk.constant.ConversationType;
import com.xiaomizhou.dpsk.controller.vo.ConversationHttp;
import com.xiaomizhou.dpsk.core.exceptions.BusinessException;
import com.xiaomizhou.dpsk.core.exceptions.OpErrorCode;
import com.xiaomizhou.dpsk.core.model.Results;
import com.xiaomizhou.dpsk.core.model.request.Request;
import com.xiaomizhou.dpsk.core.model.response.PageResponse;
import com.xiaomizhou.dpsk.core.model.response.Response;
import com.xiaomizhou.dpsk.db.ChatMessageComponent;
import com.xiaomizhou.dpsk.db.FileRecordComponent;
import com.xiaomizhou.dpsk.db.FileService;
import com.xiaomizhou.dpsk.db.chat.ChatProtocol;
import com.xiaomizhou.dpsk.db.chat.ChatService;
import com.xiaomizhou.dpsk.db.dao.ConversationDao;
import com.xiaomizhou.dpsk.db.dto.*;
import com.xiaomizhou.dpsk.db.model.ChatMessage;
import com.xiaomizhou.dpsk.utils.AuthContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

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

    private final ExecutorService executorService;

    private final FileRecordComponent fileRecordComponent;

    @PostMapping(value = "list")
    public Response<PageResponse<ConversationDto>> list(@RequestBody Request<ConversationHttp> request) {

        ConversationHttp param = request.getParam();
        if (param == null) {
            param = new ConversationHttp();
        }

        // 获取当前登录用户的 owner_code，只查询自己的会话列表
        String ownerCode = AuthContext.getAgentCode();

        ImmutablePair<Long, List<ConversationDto>> conversations = conversationDao.page(request.pageNo(), request.pageSize(), param.getType(), param.getName(), ownerCode);
        return Results.page(conversations.right, request.pageNo(), request.pageSize(), conversations.left);
    }

    @PostMapping(value = "chat/list")
    public Response<PageResponse<ChatProtocol>> chats(@RequestBody Request<ConversationHttp> request) {
        ConversationHttp param = request.getParam() != null ? request.getParam() : new ConversationHttp();
        int pageNo = request.pageNo() > 0 ? request.pageNo() : 1;
        int pageSize = request.pageSize() > 0 ? request.pageSize() : 20;

        ImmutablePair<Long, List<ChatProtocol>> result = conversationDao.chatPage(param.getConversationCode(), param.getTaskId(),pageNo, pageSize);
        return Results.page(result.right, pageNo, pageSize, result.left);
    }

    @PostMapping(value = "setTop")
    public Response<Boolean> setTop(@RequestBody Request<ConversationHttp> request) {
        ConversationHttp param = request.getParam() != null ? request.getParam() : new ConversationHttp();
        return Results.ok(conversationDao.setTop(param.getConversationCode(), param.getTop()));
    }

    /**
     * 置顶（pin）一条消息：让 AI 沉淀出的通用知识/结论在长项目中随每次 L0 窗口常驻，
     * 即使它已不在最近窗口内。
     */
    @PostMapping(value = "pin")
    public Response<Boolean> pin(@RequestBody Request<ConversationHttp> request) {
        ConversationHttp param = request.getParam();
        if (param == null || StringUtils.isBlank(param.getConversationCode())) {
            return Results.fail("conversationCode 不能为空");
        }
        if (StringUtils.isBlank(param.getPinMsgCode())) {
            return Results.fail("pinMsgCode 不能为空");
        }
        return Results.ok(conversationDao.setPinMsg(param.getConversationCode(), param.getPinMsgCode()));
    }

    /**
     * 取消置顶（pin）。
     */
    @PostMapping(value = "unpin")
    public Response<Boolean> unpin(@RequestBody Request<ConversationHttp> request) {
        ConversationHttp param = request.getParam();
        if (param == null || StringUtils.isBlank(param.getConversationCode())) {
            return Results.fail("conversationCode 不能为空");
        }
        return Results.ok(conversationDao.setPinMsg(param.getConversationCode(), null));
    }

    @PostMapping(value = "chat/cancel")
    public Response<Boolean> cancelChat(@RequestBody Request<ConversationHttp> request) {
        ConversationHttp param = request.getParam();
        if (param == null || StringUtils.isBlank(param.getMsgCode())) {
            return Results.fail("msgCode 不能为空");
        }

        boolean result = chatService.cancel(param.getMsgCode());
        return Results.ok(result);
    }

    @PostMapping(value = "chat/ignore")
    public Response<Boolean> ignoreChat(@RequestBody Request<ConversationHttp> request) {
        ConversationHttp param = request.getParam();
//        if (param == null || StringUtils.isBlank(param.getConversationCode())) {
//            return Results.fail("会话编码不能为空");
//        }

        boolean result = chatMessageComponent.ignore(param.getConversationCode(), param.getIgnoreMsgCodes());
        return Results.ok(result);
    }

    @PostMapping(value = "chat/read")
    public Response<Boolean> delivered(@RequestBody Request<ConversationHttp> request) {
        ConversationHttp param = request.getParam();
        boolean result = chatMessageComponent.delivered(param.getDeliveredMsgCodes());
        return Results.ok(result);
    }

    /**
     * 获取当前登录用户的未读消息数量（状态为 SENT 的消息），按 conversation_code 分组
     */
    @PostMapping(value = "chat/unreadCount")
    public Response<List<UnreadCountDto>> unreadCount() {
        String agentCode = AuthContext.getAgentCode();
        Map<String, Long> countMap = chatMessageComponent.countSentMessagesByConversation(agentCode);
        List<UnreadCountDto> list = countMap.entrySet().stream()
                .map(e -> new UnreadCountDto(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
        return Results.ok(list);
    }

    /**
     * 保存消息
     *
     * @param request
     * @return
     */
    @PostMapping(value = "chat/save")
    public Response<SaveMsgDto> addChat(@RequestBody Request<ChatMsgDto> request) {
        ChatMsgDto dto = request.getParam();
        if (dto == null) {
            return Results.fail("参数不能为空");
        }

        String sendCode = AuthContext.getAgentCode();

        Integer type = dto.getConversationType();

        dto.setMessageType("USER");
        String msgCode = "";
        if (ConversationType.SINGLE.getCode().equals(type)) {
            String code = chatMessageComponent.newSingleChatMsg(sendCode, dto, null, "");

            if (CollectionUtils.isNotEmpty(dto.getFileCodes()) && Objects.equals(AgentBuildSpec.ImageBuildSpec.TYPE_IMAGE2IMAGE, dto.getImageParam().getMode())) {
                dto.getImageParam().setUrls(dto.getFileCodes().stream().map(fileRecordComponent::getByCode).map(FileRecordDto::getFilePath).collect(Collectors.toList()));
            }

            chatService.doChat(sendCode, code, dto.getMcpCodes(), dto.getSkillPaths(), dto.getImageParam());
            msgCode = code;
        } else if (ConversationType.GROUP.getCode().equals(type)) {
            String code = chatMessageComponent.newGroupChatMsg(sendCode, dto, null, null);
            executorService.submit(() -> {
                chatService.doChat(sendCode, code, dto.getMcpCodes(), dto.getSkillPaths(), dto.getImageParam());
            });
            msgCode = code;
        } else if (ConversationType.WORKFLOW.getCode().equals(type)) {
            String code = chatMessageComponent.newWorkflowMsg(sendCode, dto, null, null);
            executorService.execute(() -> chatService.doChat(sendCode, code, dto.getMcpCodes(), dto.getSkillPaths(), dto.getImageParam()));
            msgCode = code;
        }

        ChatMessage msg = chatMessageComponent.getByCode(msgCode);
        if (Objects.isNull(msg)) {
            throw new BusinessException(OpErrorCode.INTERNAL_ERROR, "Unknow error.");
        }

        return Results.ok(SaveMsgDto.builder()
                .conversationCode(msg.getConversationCode())
                .conversationType(msg.getContentType())
                .msgCode(msgCode)
                .build());
    }
}

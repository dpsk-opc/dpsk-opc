package com.xiaomizhou.dpsk.controller;

import com.xiaomizhou.dpsk.db.ChatGroupComponent;
import com.xiaomizhou.dpsk.db.dto.ChatGroupDto;
import com.xiaomizhou.dpsk.db.dto.ChatMemberDto;
import com.xiaomizhou.dpsk.db.dto.GroupAddMemberCmd;
import com.xiaomizhou.dpsk.db.dto.GroupListCmd;
import com.xiaomizhou.dpsk.db.dto.GroupNewCmd;
import com.xiaomizhou.dpsk.db.dto.GroupRemoveMemberCmd;
import com.xiaomizhou.dpsk.db.dto.GroupUpdateCmd;
import com.xiaomizhou.dpsk.core.model.Results;
import com.xiaomizhou.dpsk.core.model.request.Request;
import com.xiaomizhou.dpsk.core.model.response.PageResponse;
import com.xiaomizhou.dpsk.core.model.response.Response;
import com.xiaomizhou.dpsk.utils.AuthContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 群组管理控制器
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/21 19:18
 */
@RestController
@Slf4j
@RequestMapping(value = "xiaomizhou/opc/v1/group")
@RequiredArgsConstructor
public class GroupController {

    private final ChatGroupComponent chatGroupComponent;

    /**
     * 分页查询群组列表，支持按名称模糊过滤
     *
     * @param request 查询参数（name 可选，pageNo/pageSize 分页）
     * @return 分页结果
     */
    @PostMapping(value = "list")
    public Response<PageResponse<ChatGroupDto>> list(@RequestBody Request<GroupListCmd> request) {
        GroupListCmd cmd = request.getParam();
        if (cmd == null) {
            cmd = new GroupListCmd();
        }

        ImmutablePair<Long, List<ChatGroupDto>> pair = chatGroupComponent.listGroups(
                cmd.getName(),
                request.pageNo(),
                request.pageSize());

        return Results.page(pair.right, request.pageNo(), request.pageSize(), pair.left);
    }

    /**
     * 创建新群组
     *
     * @param request 群组创建参数
     * @return 群组编码
     */
    @PostMapping(value = "newGroup")
    public Response<String> newGroup(@RequestBody Request<GroupNewCmd> request) {
        GroupNewCmd cmd = request.getParam();
        if (cmd == null) {
            return Results.fail("参数不能为空");
        }

        String groupCode = chatGroupComponent.newChatGroup(
                cmd.getName(),
                cmd.getAvatar(),
                cmd.getOwnerCode(),
                cmd.getMemberCodes());

        if (StringUtils.isBlank(groupCode)) {
            return Results.fail("创建群组失败");
        }

        return Results.ok(groupCode);
    }

    /**
     * 更新群组信息
     *
     * @param request 群组更新参数
     * @return 是否更新成功
     */
    @PostMapping(value = "updateGroup")
    public Response<Boolean> updateGroup(@RequestBody Request<GroupUpdateCmd> request) {
        GroupUpdateCmd cmd = request.getParam();
        if (cmd == null) {
            return Results.fail("参数不能为空");
        }

        boolean result = chatGroupComponent.updateGroup(
                cmd.getGroupCode(),
                cmd.getName(),
                cmd.getAvatar());

        return result ? Results.ok(true) : Results.fail("更新群组失败，群组不存在");
    }

    /**
     * 添加群组成员
     *
     * @param request 添加成员参数
     * @return 是否添加成功
     */
    @PostMapping(value = "addMember")
    public Response<Boolean> addGroupMember(@RequestBody Request<GroupAddMemberCmd> request) {
        GroupAddMemberCmd cmd = request.getParam();
        if (cmd == null) {
            return Results.fail("参数不能为空");
        }

        boolean result = chatGroupComponent.addGroupMember(
                cmd.getGroupCode(),
                cmd.getMemberCodes());

        return result ? Results.ok(true) : Results.fail("添加成员失败");
    }

    /**
     * 移除群组成员（仅群主可操作）
     *
     * @param request 移除成员参数（群编码 + 成员编码列表）
     * @return 是否移除成功
     */
    @PostMapping(value = "removeMember")
    public Response<Boolean> removeGroupMembers(@RequestBody Request<GroupRemoveMemberCmd> request) {
        GroupRemoveMemberCmd cmd = request.getParam();
        if (cmd == null) {
            return Results.fail("参数不能为空");
        }

        boolean result = chatGroupComponent.removeGroupMembers(
                cmd.getGroupCode(),
                cmd.getMemberCodes(),
                AuthContext.getAgentCode());

        return result ? Results.ok(true) : Results.fail("移除群成员失败，群聊不存在或无权操作");
    }

    /**
     * 获取群组成员列表
     *
     * @param request 群组编码
     * @return 成员列表
     */
    @PostMapping(value = "getGroupMembers")
    public Response<List<ChatMemberDto>> getGroupMembers(@RequestBody Request<String> request) {
        String groupCode = request.getParam();
        if (StringUtils.isBlank(groupCode)) {
            return Results.fail("群组编码不能为空");
        }

        List<ChatMemberDto> members = chatGroupComponent.getGroupMembers(groupCode);
        return Results.ok(members);
    }

    @PostMapping(value = "getGroupConversationCode")
    public Response<String> getGroupConversationCode(@RequestBody Request<String> request) {
        String groupCode = request.getParam();
        if (StringUtils.isBlank(groupCode)) {
            return Results.fail("群组编码不能为空");
        }


        String userId = AuthContext.getAgentCode();

        String conversationCode = chatGroupComponent.getGroupConversationCode(userId, groupCode);
        return Results.ok(conversationCode);
    }

    /**
     * 删除群聊
     * <p>
     * 群主调用：解散群聊（群、群成员关系、群会话一并删除）；
     * 普通成员调用：退出群聊（仅移除自己，不影响群内其他人）。
     *
     * @param request 群组编码
     * @return 是否删除成功
     */
    @PostMapping(value = "delete")
    public Response<Boolean> delete(@RequestBody Request<String> request) {
        String groupCode = request.getParam();
        if (StringUtils.isBlank(groupCode)) {
            return Results.fail("群组编码不能为空");
        }

        boolean result = chatGroupComponent.deleteGroup(groupCode, AuthContext.getAgentCode());
        return result ? Results.ok(true) : Results.fail("删除群聊失败，群聊不存在或无权操作");
    }

}

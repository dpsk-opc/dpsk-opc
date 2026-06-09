package com.xiaomizhou.dpsk.db;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiaomizhou.dpsk.constant.ConversationType;
import com.xiaomizhou.dpsk.db.dao.AgentDao;
import com.xiaomizhou.dpsk.db.dao.ChatGroupDao;
import com.xiaomizhou.dpsk.db.dao.ChatGroupMemberDao;
import com.xiaomizhou.dpsk.db.dao.ConversationDao;
import com.xiaomizhou.dpsk.db.dto.ChatGroupDto;
import com.xiaomizhou.dpsk.db.dto.ChatMemberDto;
import com.xiaomizhou.dpsk.db.model.Agent;
import com.xiaomizhou.dpsk.db.model.ChatGroup;
import com.xiaomizhou.dpsk.db.model.ChatGroupMember;
import com.xiaomizhou.dpsk.db.model.Conversation;
import com.xiaomizhou.dpsk.utils.SequenceUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.xiaomizhou.dpsk.utils.SequenceUtils.UUIDSequenceGenerator.CHAT_GROUP_PREFIX;
import static com.xiaomizhou.dpsk.utils.SequenceUtils.UUIDSequenceGenerator.CONVERSATION_PREFIX;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/21 18:12
 * @description
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ChatGroupComponent {

    @Value("${com.xiaomizhou.opc.im.group.max-member-count:20}")
    private Integer maxMemberCount;

    private final ChatGroupDao chatGroupDao;

    private final ChatGroupMemberDao chatGroupMemberDao;

    private final ConversationDao conversationDao;

    private final AgentDao agentDao;

    /**
     * 分页查询群组列表，支持按名称模糊查询
     *
     * @param name     群组名称（模糊匹配），可为空
     * @param pageNo   页码
     * @param pageSize 每页数量
     * @return 分页结果
     */
    public ImmutablePair<Long, List<ChatGroupDto>> listGroups(String name, int pageNo, int pageSize) {
        LambdaQueryWrapper<ChatGroup> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(name)) {
            wrapper.like(ChatGroup::getName, name);
        }


        long cnt = chatGroupDao.count();

        if (0 == cnt) {
            return ImmutablePair.of(0L, List.of());
        }


        List<ChatGroup> list = chatGroupDao.list(wrapper.last("limit %s,%s".formatted((pageNo - 1) * pageSize, pageSize)).orderByAsc(ChatGroup::getId));


        // 收集所有 groupCode，批量查询成员数量
        List<String> groupCodes = list.stream()
                .map(ChatGroup::getCode)
                .collect(Collectors.toList());

        Map<String, Long> memberCountMap = Map.of();
        if (CollectionUtils.isNotEmpty(groupCodes)) {
            List<ChatGroupMember> members = chatGroupMemberDao.lambdaQuery()
                    .in(ChatGroupMember::getChatGroupCode, groupCodes)
                    .list();
            memberCountMap = members.stream()
                    .collect(Collectors.groupingBy(ChatGroupMember::getChatGroupCode, Collectors.counting()));
        }
        Map<String, Long> finalMemberCountMap = memberCountMap;
        return ImmutablePair.of(cnt, list.stream().map(group -> convertToDto(group, finalMemberCountMap.getOrDefault(group.getCode(), 0L))).toList());
    }

    /**
     * ChatGroup 实体转 DTO
     */
    private ChatGroupDto convertToDto(ChatGroup group, Long memberCount) {
        ChatGroupDto dto = new ChatGroupDto();
        dto.setCode(group.getCode());
        dto.setName(group.getName());
        dto.setAvatar(group.getAvatar());
        dto.setOwnerCode(group.getOwnerCode());
        dto.setAnnouncement(group.getAnnouncement());
        dto.setStatus(group.getStatus());
        dto.setLastMessageCode(group.getLastMessageCode());
        dto.setExtConfig(group.getExtConfig());
        dto.setCreateTime(group.getCreateTime());
        dto.setUpdateTime(group.getUpdateTime());
        dto.setMemberCount(memberCount);
        return dto;
    }


    /**
     * 更新群组信息
     *
     * @param groupCode
     * @param name
     * @param avatar
     * @return
     */
    public boolean updateGroup(String groupCode, String name, String avatar) {
        ChatGroup group = chatGroupDao.getByCode(groupCode);
        if (Objects.isNull(group)) {
            return false;
        }

        ChatGroup model = new ChatGroup();
        model.setId(group.getId());
        model.setName(name);
        model.setAvatar(avatar);
        model.setUpdateTime(new Date());

        return chatGroupDao.updateById(model);
    }

    /**
     * 获取群组成员
     *
     * @param groupCode
     * @return
     */
    public List<ChatMemberDto> getGroupMembers(String groupCode) {

        if (StringUtils.isBlank(groupCode)) {
            return List.of();
        }

        ChatGroup group = chatGroupDao.getByCode(groupCode);
        if (Objects.isNull(group)) {
            return List.of();
        }

        List<ChatGroupMember> members = chatGroupMemberDao.lambdaQuery().eq(ChatGroupMember::getChatGroupCode, groupCode).list();
        if (CollectionUtils.isEmpty(members)) {
            return List.of();
        }

        Map<String, Agent> agents = agentDao.lambdaQuery().in(Agent::getCode, members.stream()
                        .map(ChatGroupMember::getAgentCode).collect(Collectors.toList()))
                .list()
                .stream()
                .collect(Collectors.toMap(Agent::getCode, Function.identity(), (k1, k2) -> k2));


        return members.stream().map(member -> {
            Agent agent = agents.get(member.getAgentCode());
            if (Objects.isNull(agent)) {
                return null;
            }
            ChatMemberDto dto = new ChatMemberDto();
            dto.setType(agent.getType());
            dto.setName(agent.getName());
            dto.setNicknameInGroup(member.getNicknameInGroup());
            dto.setAvatar(agent.getAvatar());
            dto.setRoleInGroup(member.getRole());
            dto.setCode(agent.getCode());
            return dto;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }


    /**
     * 添加群组成员
     *
     * @param groupCode
     * @param memberCodes
     * @return
     */
    public boolean addGroupMember(String groupCode, List<String> memberCodes) {

        if (StringUtils.isBlank(groupCode) || CollectionUtils.isEmpty(memberCodes)) {
            return false;
        }

        ChatGroup group = chatGroupDao.getByCode(groupCode);
        if (Objects.isNull(group)) {
            return false;
        }

        List<ChatGroupMember> list = chatGroupMemberDao.lambdaQuery().eq(ChatGroupMember::getChatGroupCode, groupCode).in(ChatGroupMember::getAgentCode, memberCodes).list();
        if (CollectionUtils.isNotEmpty(list)) {
            log.warn("group {} has member {}", groupCode, memberCodes);
            return false;
        }

        long cnt = chatGroupMemberDao.lambdaQuery().eq(ChatGroupMember::getChatGroupCode, groupCode).count();
        if (cnt + memberCodes.size() > maxMemberCount) {
            return false;
        }


        List<ChatGroupMember> members = new ArrayList<>();
        memberCodes.forEach(memberCode -> {
            ChatGroupMember member = new ChatGroupMember();
            member.setChatGroupCode(groupCode);
            member.setAgentCode(memberCode);
            member.setRole("MEMBER");
            member.setStatus(0);
            member.setJoinTime(new Date());
            member.setCreateTime(new Date());
            member.setUpdateTime(new Date());
            members.add(member);
        });
        chatGroupMemberDao.saveBatch(members);

        return true;
    }


    /**
     * 创建新的群组
     *
     * @param name
     * @param avatar
     * @param ownerCode
     * @param memberCodes
     * @return
     */
    public String newChatGroup(String name, String avatar, String ownerCode, List<String> memberCodes) {

        if (StringUtils.isBlank(ownerCode) || CollectionUtils.isEmpty(memberCodes) || memberCodes.size() > maxMemberCount) {
            return null;
        }

        String groupCode = SequenceUtils.generator().next(CHAT_GROUP_PREFIX);

        // save group
        ChatGroup group = new ChatGroup();
        group.setCode(groupCode);

        if (StringUtils.isBlank(name)) {
            List<Agent> agents = agentDao.lambdaQuery().in(Agent::getCode, memberCodes.subList(0, 3)).select(Agent::getName).list();
            name = agents.stream().map(Agent::getName).collect(Collectors.joining(","));
        }

        group.setName(name);
        group.setAvatar(avatar);
        group.setOwnerCode(ownerCode);
        group.setStatus(0);
        group.setAvatar(avatar);
        group.setCreateTime(new Date());
        group.setUpdateTime(new Date());
        group.setExtConfig("{}");

        chatGroupDao.save(group);

        // save chat menbers
        memberCodes.add(ownerCode);
        memberCodes = memberCodes.stream().distinct().collect(Collectors.toList());
        // save the members
        List<ChatGroupMember> members = new ArrayList<>();
        memberCodes.forEach(memberCode -> {
            ChatGroupMember member = new ChatGroupMember();
            member.setChatGroupCode(groupCode);
            member.setAgentCode(memberCode);
            member.setRole(Strings.CS.equals(memberCode, ownerCode) ? "OWNER" : "MEMBER");
            member.setStatus(0);
            member.setJoinTime(new Date());
            member.setCreateTime(new Date());
            member.setUpdateTime(new Date());
            members.add(member);
        });
        chatGroupMemberDao.saveBatch(members);

        // save conversation
        Conversation conversation = new Conversation();
        conversation.setCode(SequenceUtils.generator().next(CONVERSATION_PREFIX));
        conversation.setConversationType(1);
        conversation.setTargetCode(groupCode);
        conversation.setOwnerCode(ownerCode);
        conversation.setTargetCode(groupCode);
        conversation.setCreateTime(new Date());
        conversation.setUpdateTime(new Date());
        conversationDao.save(conversation);

        return groupCode;
    }


    /**
     * 获取群组对话的code
     *
     * @param userId
     * @param groupCode
     * @return
     */
    public String getGroupConversationCode(String userId, String groupCode) {
        if (StringUtils.isAnyBlank(groupCode, userId)) {
            return "";
        }
        Conversation one = conversationDao.getOne(userId, groupCode, ConversationType.GROUP.getCode());
        return Optional.ofNullable(one).map(Conversation::getCode).orElse("");
    }
}

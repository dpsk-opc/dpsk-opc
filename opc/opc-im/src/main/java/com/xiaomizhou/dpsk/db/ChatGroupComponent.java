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
import com.xiaomizhou.dpsk.tool.workspace.WorkspaceInitializer;
import com.xiaomizhou.dpsk.tool.workspace.WorkspaceProperties;
import com.xiaomizhou.dpsk.tool.workspace.WorkspaceScope;
import com.xiaomizhou.dpsk.utils.SequenceUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * 工作空间配置。使用 ObjectProvider 懒加载，避免构造期循环依赖。
     */
    private final ObjectProvider<WorkspaceProperties> workspacePropertiesProvider;

    /**
     * 工作空间初始化器。使用 ObjectProvider 懒加载，避免构造期循环依赖。
     */
    private final ObjectProvider<WorkspaceInitializer> workspaceInitializerProvider;

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
        dto.setWorkspace(group.getWorkspace());
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
        return updateGroup(groupCode, name, avatar, null);
    }

    /**
     * 更新群组信息（含工作空间）。
     *
     * @param workspace 群工作空间（公共产出目录）；传 null 表示不修改
     */
    public boolean updateGroup(String groupCode, String name, String avatar, String workspace) {
        ChatGroup group = chatGroupDao.getByCode(groupCode);
        if (Objects.isNull(group)) {
            return false;
        }

        ChatGroup model = new ChatGroup();
        model.setId(group.getId());
        model.setName(name);
        model.setAvatar(avatar);
        if (workspace != null) {
            // 修改时同样回填默认值并确保目录存在（清空 workspace 视为恢复默认）
            model.setWorkspace(resolveAndInitWorkspace(workspace, group.getCode()));
        }
        model.setUpdateTime(new Date());

        return chatGroupDao.updateById(model);
    }

    /**
     * 解析最终工作空间并确保目录存在。
     * <p>
     * 用户未配置时实时推导默认值 {@code <root>/<groupCode>}，
     * 避免"新建群后未重启"期间 workspace 为空。
     *
     * @param configured 用户配置的 workspace（可为空）
     * @param groupCode  群编码
     * @return 最终生效的工作空间路径
     */
    private String resolveAndInitWorkspace(String configured, String groupCode) {
        WorkspaceProperties properties = workspacePropertiesProvider.getIfAvailable();
        if (properties == null || !properties.isEnabled() || StringUtils.isBlank(groupCode)) {
            return configured;
        }
        String workspace = properties.resolve(configured, groupCode);
        if (StringUtils.isBlank(workspace)) {
            return configured;
        }
        WorkspaceInitializer initializer = workspaceInitializerProvider.getIfAvailable();
        if (initializer != null) {
            try {
                initializer.initialize(WorkspaceScope.builder().primaryWorkspace(workspace).build());
            } catch (Exception e) {
                log.warn("Failed to init workspace dir for group '{}': {}", groupCode, workspace, e);
            }
        }
        return workspace;
    }

    /**
     * 查询全部群组（供启动期工作空间初始化使用）。
     */
    public List<ChatGroup> listAll() {
        return chatGroupDao.list();
    }

    /**
     * 获取群的工作空间（公共产出目录）。未配置返回 null。
     *
     * @param groupCode 群编码
     */
    public String getWorkspace(String groupCode) {
        if (StringUtils.isBlank(groupCode)) {
            return null;
        }
        ChatGroup group = chatGroupDao.getByCode(groupCode);
        if (group == null || StringUtils.isBlank(group.getWorkspace())) {
            return null;
        }
        return group.getWorkspace();
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
            dto.setNickname(agent.getNickname());
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
        // 立即回填默认工作空间（公共产出目录）并创建目录，
        // 避免"新建群后未重启"期间 workspace 为空
        group.setWorkspace(resolveAndInitWorkspace(null, groupCode));

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
     * 移除群组成员（仅群主可操作）
     * <p>
     * 将指定成员从群内移除（物理删除成员关系），群主自身不可被移除。
     * 采用物理删除而非逻辑删除，避免唯一索引 uk_group_member 被已删除记录占位，
     * 导致被移除成员重新入群时唯一键冲突。
     *
     * @param groupCode    群组编码
     * @param memberCodes  待移除的成员编码列表
     * @param operatorCode 操作人（当前登录用户，须为群主）
     * @return 是否移除成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean removeGroupMembers(String groupCode, List<String> memberCodes, String operatorCode) {
        if (StringUtils.isAnyBlank(groupCode, operatorCode) || CollectionUtils.isEmpty(memberCodes)) {
            return false;
        }

        ChatGroup group = chatGroupDao.getByCode(groupCode);
        if (Objects.isNull(group)) {
            return false;
        }

        // 仅群主可移除群成员
        if (!Strings.CS.equals(group.getOwnerCode(), operatorCode)) {
            log.warn("非群主无权移除群成员, groupCode={}, operator={}", groupCode, operatorCode);
            return false;
        }

        // 过滤空值、去重，并排除群主自身
        List<String> targets = memberCodes.stream()
                .filter(StringUtils::isNotBlank)
                .distinct()
                .filter(code -> !Strings.CS.equals(code, group.getOwnerCode()))
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(targets)) {
            return false;
        }

        // 仅处理群内已存在的成员关系
        List<ChatGroupMember> members = chatGroupMemberDao.lambdaQuery()
                .eq(ChatGroupMember::getChatGroupCode, groupCode)
                .in(ChatGroupMember::getAgentCode, targets)
                .list();
        if (CollectionUtils.isEmpty(members)) {
            return false;
        }

        int removed = 0;
        for (ChatGroupMember member : members) {
            if (chatGroupMemberDao.physicalDeleteById(member.getId())) {
                removed++;
            }
        }

        log.info("移除群成员完成, groupCode={}, operator={}, removed={}", groupCode, operatorCode, removed);
        return removed > 0;
    }

    /**
     * 删除/解散群聊
     * <p>
     * 群主调用：解散群聊。物理删除群、群成员关系以及该群的群聊会话（群会话全群只有一条，归属群主）。
     * 普通成员调用：退出群聊。物理删除自己的成员关系，不影响群内其他人。
     * <p>
     * 采用物理删除而非逻辑删除，避免 t_chat_group.uk_group_code、
     * t_chat_group_member.uk_group_member、t_conversation.udx_conversation_owner_target
     * 等唯一索引被已删除记录占位，导致重新建群/重新入群时唯一键冲突。
     *
     * @param groupCode    群组编码
     * @param operatorCode 操作人（当前登录用户）
     * @return 是否删除成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteGroup(String groupCode, String operatorCode) {
        if (StringUtils.isAnyBlank(groupCode, operatorCode)) {
            return false;
        }

        ChatGroup group = chatGroupDao.getByCode(groupCode);
        if (Objects.isNull(group)) {
            return false;
        }

        // 群主：解散群聊（物理删除群、群成员关系与群会话）
        if (Strings.CS.equals(group.getOwnerCode(), operatorCode)) {
            chatGroupDao.physicalDeleteByCode(groupCode);
            chatGroupMemberDao.physicalDeleteByGroupCode(groupCode);
            conversationDao.physicalDeleteByTypeAndTarget(ConversationType.GROUP.getCode(), groupCode);

            log.info("解散群聊成功, groupCode={}, owner={}", groupCode, operatorCode);
            return true;
        }

        // 普通成员：退出群聊（物理删除自己的成员关系）
        ChatGroupMember member = chatGroupMemberDao.lambdaQuery()
                .eq(ChatGroupMember::getChatGroupCode, groupCode)
                .eq(ChatGroupMember::getAgentCode, operatorCode)
                .one();
        if (Objects.isNull(member)) {
            return false;
        }

        chatGroupMemberDao.physicalDeleteById(member.getId());

        log.info("退出群聊成功, groupCode={}, member={}", groupCode, operatorCode);
        return true;
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

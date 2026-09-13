package com.xiaomizhou.dpsk.db;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaomizhou.dpsk.constant.OwnerType;
import com.xiaomizhou.dpsk.db.dao.AgentAuthTokenDao;
import com.xiaomizhou.dpsk.db.dao.AgentDao;
import com.xiaomizhou.dpsk.db.dao.AgentToolRefDao;
import com.xiaomizhou.dpsk.db.dao.ContactDao;
import com.xiaomizhou.dpsk.db.dto.*;
import com.xiaomizhou.dpsk.db.model.Agent;
import com.xiaomizhou.dpsk.db.model.AgentAuthToken;
import com.xiaomizhou.dpsk.core.exceptions.BusinessException;
import com.xiaomizhou.dpsk.db.model.KnowledgeLib;
import com.xiaomizhou.dpsk.planning.CapabilityExtractor;
import com.xiaomizhou.dpsk.utils.JsonUtils;
import com.xiaomizhou.dpsk.utils.PasswordEncoder;
import com.xiaomizhou.dpsk.utils.SequenceUtils;
import com.xiaomizhou.dpsk.utils.TokenUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.xiaomizhou.dpsk.utils.SequenceUtils.UUIDSequenceGenerator.AGENT_PREFIX;
import static com.xiaomizhou.dpsk.utils.SequenceUtils.UUIDSequenceGenerator.TOKEN_PREFIX;

/**
 * Agent 业务处理类
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/18
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AgentComponent {

    private final AgentDao agentDao;
    private final AgentAuthTokenDao agentAuthTokenDao;
    private final PasswordEncoder passwordEncoder;
    private final ContactDao contactDao;

    private final AgentToolComponent agentToolComponent;
    private final KnowledgeLibComponent knowledgeLibComponent;

    /**
     * 能力标签提取器。使用 ObjectProvider 懒加载，打破
     * AgentComponent → CapabilityExtractor → … → ImAgentDefProvider → AgentComponent 的构造期循环依赖。
     */
    private final ObjectProvider<CapabilityExtractor> capabilityExtractorProvider;

    /**
     * 是否是真实用户
     *
     * @param agentCode
     * @return
     */
    public boolean isUser(String agentCode) {
        if (StringUtils.isBlank(agentCode)) {
            return false;
        }

        Agent agent = agentDao.getByCode(agentCode);
        return Objects.nonNull(agent) && Strings.CI.equals(agent.getType(), "USER");
    }

    /**
     * 分页查询（仅查询好友列表中的 Agent）
     */
    public IPage<AgentDto> queryPage(AgentQueryParam param, String ownerCode) {
        LambdaQueryWrapper<Agent> wrapper = new LambdaQueryWrapper<>();

        // 根据当前登录用户过滤：只能查自己好友列表中的 Agent
        List<String> friendCodes = contactDao.listFriendCodes(ownerCode);
        if (CollectionUtils.isEmpty(friendCodes)) {
            // 没有好友，返回空结果
            IPage<AgentDto> emptyPage = new Page<>(param.getPageNo(), param.getPageSize());
            emptyPage.setTotal(0);
            return emptyPage;
        }
        wrapper.in(Agent::getCode, friendCodes);

        if (StringUtils.isNotBlank(param.getCode())) {
            wrapper.eq(Agent::getCode, param.getCode());
        }
        if (StringUtils.isNotBlank(param.getName())) {
            wrapper.like(Agent::getName, param.getName());
        }
        if (StringUtils.isNotBlank(param.getNickname())) {
            wrapper.like(Agent::getNickname, param.getNickname());
        }
        if (StringUtils.isNotBlank(param.getType())) {
            wrapper.eq(Agent::getType, param.getType());
        }
        if (StringUtils.isNotBlank(param.getStatus())) {
            wrapper.eq(Agent::getStatus, param.getStatus());
        }
        if (StringUtils.isNotBlank(param.getRole())) {
            wrapper.eq(Agent::getRole, param.getRole());
        }
        if (StringUtils.isNotBlank(param.getMbti())) {
            wrapper.eq(Agent::getMbti, param.getMbti());
        }
        if (param.getSex() != null) {
            wrapper.eq(Agent::getSex, param.getSex());
        }
        // 关键词搜索
        if (StringUtils.isNotBlank(param.getKeyword())) {
            wrapper.and(w -> w
                    .like(Agent::getName, param.getKeyword())
                    .or()
                    .like(Agent::getNickname, param.getKeyword())
                    .or()
                    .like(Agent::getDescription, param.getKeyword()));
        }
        if (param.getModality() != null) {
            wrapper.eq(Agent::getModality, param.getModality());
        }
        // 排除已删除的记录
        wrapper.eq(Agent::getIsDeleted, 0);

        Page<Agent> page = new Page<>(param.getPageNo(), param.getPageSize());
        IPage<Agent> agentPage = agentDao.page(page, wrapper);


        IPage<AgentDto> results = agentPage.convert(this::convertToDto);

        List<String> agentCodes = results.getRecords().stream().map(AgentDto::getCode).collect(Collectors.toList());
        if (CollectionUtils.isEmpty(agentCodes)) {
            return results;
        }

        Map<String, List<AgentToolRefVO>> tools = agentToolComponent.queryByAgentCodes(agentCodes);
        results.getRecords().forEach(agentDto -> {
            agentDto.setTools(tools.get(agentDto.getCode()));
        });

        // 设置知识库数量
        Map<String, KnowledgeLib> libs = knowledgeLibComponent.listByOwnerCodes(agentCodes, OwnerType.AGENT).stream().collect(Collectors.toMap(KnowledgeLib::getOwnerCode, Function.identity(), (k1, k2) -> k1));
        if (MapUtils.isEmpty(libs)) {
            return results;
        }

        results.getRecords().forEach(agentDto -> {
            if (!libs.containsKey(agentDto.getCode())) {
                return;
            }
            KnowledgeLib lib = libs.get(agentDto.getCode());
            agentDto.setKnowledgeLibCode(Objects.isNull(lib) ? "" : lib.getCode());
        });

        return results;
    }

    /**
     * 根据ID查询
     */
    public AgentDto getById(Long id) {
        Agent agent = agentDao.getById(id);
        return convertToDto(agent);
    }

    public List<AgentDto> getByCodes(Collection<String> codes) {

        if (CollectionUtils.isEmpty(codes)) {
            return List.of();
        }

        return agentDao.lambdaQuery()
                .in(Agent::getCode, codes)
                .list()
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * get one by code
     *
     * @param code
     * @return
     */
    public AgentDto getByCode(String code) {
        Agent agent = agentDao.getByCode(code);
        if (agent == null) {
            return null;
        }
        AgentDto dto = convertToDto(agent);

        // 填充工具列表
        Map<String, List<AgentToolRefVO>> tools = agentToolComponent.queryByAgentCodes(List.of(code));
        dto.setTools(tools.get(code));

        // 填充知识库编码
        List<KnowledgeLib> libs = knowledgeLibComponent.listByOwnerCodes(List.of(code), OwnerType.AGENT);
        if (CollectionUtils.isNotEmpty(libs)) {
            dto.setKnowledgeLibCode(libs.get(0).getCode());
        }

        return dto;
    }


    /**
     * 创建
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentDto create(String loginUserCode,AgentCreateCmd cmd) {
        Agent agent = new Agent();

        agent.setCode(SequenceUtils.generator().next(AGENT_PREFIX));
        agent.setName(cmd.getName());
        agent.setNickname(cmd.getNickname());
        agent.setSex(cmd.getSex());
        agent.setMbti(cmd.getMbti());
        agent.setPrompt(cmd.getPrompt());
        agent.setWorkspace(cmd.getWorkspace());
        agent.setRole(cmd.getRole());
        agent.setDescription(cmd.getDescription());
        agent.setType(cmd.getType());
        agent.setAvatar(cmd.getAvatar());
        agent.setModality(cmd.getModality());
        agent.setStatus("ACTIVE");
        agent.setIntegrationConfig("");
        agent.setLlmConfig(StringUtils.defaultString(cmd.getLlmConfig(), ""));
        agent.setLastActiveTime(new Date());

        // 新增时同步提取能力标签（若 prompt 为空则跳过）
        if (StringUtils.isNotBlank(cmd.getPrompt())) {
            CapabilityExtractor extractor = capabilityExtractorProvider.getIfAvailable();
            if (extractor != null) {
                List<String> tags = extractor.extract(cmd.getPrompt());
                if (!tags.isEmpty()) {
                    agent.setCapabilities(JsonUtils.toJson(tags));
                }
            }
        }

        agent.setCreateTime(new Date());
        agent.setUpdateTime(new Date());

        agentDao.save(agent);

        // 初始化知识库（一个 Agent 一个知识库）
        knowledgeLibComponent.init(agent.getCode(), agent.getName());

        List<String> tools = cmd.getTools();
        if (CollectionUtils.isNotEmpty(tools)) {
            AgentToolBindCmd bind = new AgentToolBindCmd();
            bind.setAgentCode(agent.getCode());
            bind.setToolCodes(tools);
            agentToolComponent.bindTools(bind);
        }

        // 新增好友关系
        contactDao.addFriendRelation(agent.getCode(), loginUserCode);

        log.info("创建Agent成功, id={}", agent.getId());
        return convertToDto(agent);
    }

    /**
     * 更新
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentDto update(AgentUpdateCmd cmd) {
        Agent agent = agentDao.getByCode(cmd.getCode());
        if (agent == null) {
            throw BusinessException.notFound("Agent不存在, code=" + cmd.getCode());
        }

        // 捕获旧的 prompt（DB 现值），供下方判断"prompt 是否真的变化"使用（避免其被 setPrompt 覆盖后丢失）
        String oldPrompt = agent.getPrompt();

        if (StringUtils.isNotBlank(cmd.getName())) {
            agent.setName(cmd.getName());
        }
        if (cmd.getNickname() != null) {
            agent.setNickname(cmd.getNickname());
        }
        if (cmd.getSex() != null) {
            agent.setSex(cmd.getSex());
        }
        if (cmd.getMbti() != null) {
            agent.setMbti(cmd.getMbti());
        }
        if (cmd.getPrompt() != null) {
            agent.setPrompt(cmd.getPrompt());
        }
        if (cmd.getWorkspace() != null) {
            agent.setWorkspace(cmd.getWorkspace());
        }
        if (cmd.getRole() != null) {
            agent.setRole(cmd.getRole());
        }
        if (cmd.getDescription() != null) {
            agent.setDescription(cmd.getDescription());
        }
        if (StringUtils.isNotBlank(cmd.getAvatar())) {
            agent.setAvatar(cmd.getAvatar());
        }
        if (StringUtils.isNotBlank(cmd.getStatus())) {
            agent.setStatus(cmd.getStatus());
        }
        if (cmd.getLlmConfig() != null) {
            agent.setLlmConfig(cmd.getLlmConfig());
        }
        if (cmd.getModality() != null) {
            agent.setModality(cmd.getModality());
        }

        // 能力标签：优先前端手动指定；否则仅当 prompt 真正发生变化时才由 LLM 从 prompt 重新提取（同步）。
        // 优化：仅修改 name/avatar 等无关字段而 prompt 未变（即便前端把整个 prompt 原样传回）时，不重复触发 LLM 抽取。
        if (CollectionUtils.isNotEmpty(cmd.getCapabilities())) {
            agent.setCapabilities(JsonUtils.toJson(cmd.getCapabilities()));
        } else if (cmd.getPrompt() != null && !cmd.getPrompt().equals(oldPrompt)) {
            CapabilityExtractor extractor = capabilityExtractorProvider.getIfAvailable();
            if (extractor != null) {
                List<String> tags = extractor.extract(cmd.getPrompt());
                agent.setCapabilities(tags.isEmpty() ? null : JsonUtils.toJson(tags));
            }
        }

        agent.setIntegrationConfig(cmd.getIntegrationConfig());
        agent.setType(cmd.getType());

        agent.setUpdateTime(new Date());
        agentDao.updateById(agent);
        log.info("更新Agent成功, id={}", agent.getId());

        return convertToDto(agent);
    }

    /**
     * 删除（逻辑删除），同时清理好友关系
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String code) {
        Agent agent = agentDao.getByCode(code);
        if (agent == null) {
            return;
        }

        Agent model = new Agent();
        model.setId(agent.getId());
        model.setUpdateTime(new Date());
        agentDao.removeById(model);

        // 连带删除该 Agent 的好友关系（作为 owner 和作为 friend 的都要删）
        contactDao.deleteByOwnerCode(code);
        contactDao.deleteByFriendCode(code);
        log.info("删除Agent成功, code={}, 已清理好友关系", code);
    }

    /**
     * 删除好友关系（仅解除好友关系，不删除 Agent 本身）
     * <p>
     * 好友关系是双向的（t_contact 中两个方向各一条），这里两个方向都会清理，
     * 保证双方的好友列表都不再出现对方。
     * 若需要彻底删除 Agent（含其知识库、工具绑定等），请调用 {@link #delete(String)}。
     *
     * @param ownerCode  当前登录用户 code
     * @param friendCode 好友 code
     * @return 是否删除成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteFriend(String ownerCode, String friendCode) {
        if (StringUtils.isAnyBlank(ownerCode, friendCode) || Strings.CS.equals(ownerCode, friendCode)) {
            return false;
        }

        boolean removed = contactDao.deleteByOwnerAndFriend(ownerCode, friendCode);
        // 反向关系同步清理，避免对方好友列表仍能看到已删除的好友
        contactDao.deleteByOwnerAndFriend(friendCode, ownerCode);

        log.info("删除好友关系, owner={}, friend={}, removed={}", ownerCode, friendCode, removed);
        return removed;
    }

    /**
     * 更新最后活跃时间
     */
    public void updateLastActiveTime(Long id) {
        agentDao.updateLastActiveTime(id);
    }

    /**
     * 根据类型查询Agent列表
     */
    public List<AgentDto> findByType(String type) {
        return agentDao.findByType(type)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * 查询所有活跃的Agent
     */
    public List<AgentDto> findAllActive() {
        return agentDao.findAllActive()
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * 用户登录
     *
     * @param cmd 登录命令（email + password）
     * @return AuthDto（含 token + agent 信息）
     */
    @Transactional(rollbackFor = Exception.class)
    public AuthDto login(LoginCmd cmd) {
        Agent agent = agentDao.getByEmail(cmd.getEmail());
        if (agent == null) {
            throw BusinessException.authFailed("账号或密码错误");
        }
        if (!passwordEncoder.matches(cmd.getPassword(), agent.getPassword())) {
            throw BusinessException.authFailed("账号或密码错误");
        }
        // 更新最后活跃时间
        agentDao.updateLastActiveTime(agent.getId());
        agent.setLastActiveTime(new Date());
        agent.setUpdateTime(new Date());

        // 生成 Token 并持久化
        String token = writeToken(agent.getId());

        AgentDto dto = convertToDto(agent);
        return new AuthDto(token, dto);
    }

    /**
     * 用户注册
     *
     * @param cmd 注册命令（email + password + name）
     * @return AuthDto（含 token + agent 信息）
     */
    @Transactional(rollbackFor = Exception.class)
    public AuthDto register(RegisterCmd cmd) {
        // 检查邮箱是否已存在
        Agent exist = agentDao.getByEmail(cmd.getEmail());
        if (exist != null) {
            throw BusinessException.conflict("该邮箱已被注册");
        }

        Agent agent = new Agent();
        agent.setCode(SequenceUtils.generator().next(AGENT_PREFIX));
        agent.setName(cmd.getName());
        agent.setNickname(StringUtils.defaultString(cmd.getNickname(), cmd.getName()));
        agent.setEmail(cmd.getEmail());
        agent.setPassword(passwordEncoder.encode(cmd.getPassword()));
        agent.setType("USER");
        agent.setStatus("ACTIVE");
        agent.setSex(0);
        agent.setCreateTime(new Date());
        agent.setUpdateTime(new Date());
        agent.setLastActiveTime(new Date());
        agent.setIntegrationConfig("{}");

        agentDao.save(agent);
        log.info("用户注册成功, email={}, code={}", cmd.getEmail(), agent.getCode());

        // 初始化知识库
        knowledgeLibComponent.init(agent.getCode(), agent.getName());

        // 新用户与所有已有用户建立双向好友关系
//        List<Agent> allUsers = agentDao.findByType("USER");
//        for (Agent existingUser : allUsers) {
//            if (!existingUser.getCode().equals(agent.getCode())) {
//                contactDao.addFriendRelation(agent.getCode(), existingUser.getCode());
//            }
//        }
//        log.info("新用户 {} 已与 {} 个已有用户建立好友关系", agent.getCode(), allUsers.size() - 1);

        // 生成 Token 并持久化
        String token = writeToken(agent.getId());

        AgentDto dto = convertToDto(agent);
        return new AuthDto(token, dto);
    }

    /**
     * 修改密码
     *
     * @param cmd 修改密码命令（email + oldPassword + newPassword）
     */
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangePasswordCmd cmd) {
        Agent agent = agentDao.getByEmail(cmd.getEmail());
        if (agent == null) {
            throw BusinessException.notFound("用户不存在");
        }
        if (!passwordEncoder.matches(cmd.getOldPassword(), agent.getPassword())) {
            throw BusinessException.authFailed("旧密码不正确");
        }

        Agent update = new Agent();
        update.setId(agent.getId());
        update.setPassword(passwordEncoder.encode(cmd.getNewPassword()));
        update.setUpdateTime(new Date());
        agentDao.updateById(update);

        // 密码修改后撤销所有旧 Token，强制重新登录
        agentAuthTokenDao.revokeByAgentId(agent.getId());

        log.info("密码修改成功, email={}", cmd.getEmail());
    }

    /**
     * 用户登出
     *
     * @param agentCode   Agent 编码（来自请求头）
     * @param accessToken 访问令牌（来自请求头）
     */
    @Transactional(rollbackFor = Exception.class)
    public void logout(String agentCode, String accessToken) {
        Agent agent = agentDao.getByCode(agentCode);
        if (agent == null) {
            throw BusinessException.notFound("用户不存在");
        }
        AgentAuthToken tokenRecord = agentAuthTokenDao.getActiveByAgentIdAndToken(agent.getId(), accessToken);
        if (tokenRecord == null) {
            throw BusinessException.authFailed("Token 无效或已过期");
        }
        agentAuthTokenDao.revokeByAgentId(agent.getId());
        log.info("用户登出成功, agentCode={}, agentId={}", agentCode, agent.getId());
    }

    /**
     * 生成 Token 并写入 t_agent_auth_token 表
     *
     * @param agentId Agent ID
     * @return token 字符串
     */
    private String writeToken(Long agentId) {
        String token = TokenUtils.generateToken();
        AgentAuthToken tokenRecord = new AgentAuthToken();
        tokenRecord.setCode(SequenceUtils.generator().next(TOKEN_PREFIX));
        tokenRecord.setAgentCode(agentId);
        tokenRecord.setToken(token);
        tokenRecord.setExpireTime(TokenUtils.generateExpireTime());
        tokenRecord.setStatus("ACTIVE");
        tokenRecord.setCreateTime(new Date());
        tokenRecord.setUpdateTime(new Date());
        agentAuthTokenDao.upsertToken(tokenRecord);
        return token;
    }

    /**
     * 实体转DTO
     */
    private AgentDto convertToDto(Agent agent) {
        if (agent == null) {
            return null;
        }

        AgentDto dto = new AgentDto();
        dto.setId(agent.getId());
        dto.setCode(agent.getCode());
        dto.setName(agent.getName());
        dto.setNickname(agent.getNickname());
        dto.setSex(agent.getSex());
        dto.setMbti(agent.getMbti());
        dto.setPrompt(agent.getPrompt());
        dto.setWorkspace(agent.getWorkspace());
        dto.setRole(agent.getRole());
        dto.setDescription(agent.getDescription());
        dto.setType(agent.getType());
        dto.setAvatar(agent.getAvatar());
        dto.setStatus(agent.getStatus());
        dto.setIntegrationConfig(agent.getIntegrationConfig());
        dto.setLlmConfig(agent.getLlmConfig());
        dto.setCapabilities(parseCapabilities(agent.getCapabilities()));
        dto.setModality(agent.getModality());
        dto.setLastActiveTime(agent.getLastActiveTime());
        dto.setCreateTime(agent.getCreateTime());
        dto.setUpdateTime(agent.getUpdateTime());
        dto.setDepartment("运营部");
        dto.setSlogan(agent.getSlogan());

        return dto;
    }

    /**
     * 将 capabilities（JSON 数组字符串）解析为 List&lt;String&gt;；空/非法时返回空列表。
     */
    private List<String> parseCapabilities(String capabilities) {
        if (StringUtils.isBlank(capabilities)) {
            return Collections.emptyList();
        }
        try {
            return JsonUtils.toObj(capabilities, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            log.warn("parse capabilities failed, raw={}", capabilities, e);
            return Collections.emptyList();
        }
    }
}

-- =============================================
-- 表名: t_agent_auth_token
-- 描述: Agent认证Token表，存储Agent的认证Token信息
-- 规范: 所有字段 NOT NULL + 默认值，包含通用字段
-- =============================================
CREATE TABLE IF NOT EXISTS t_agent_auth_token (
    -- 主键
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',

    -- 业务字段
    code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '业务编码，唯一标识一条Token记录，如 token_001',
    agent_code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '关联的Agent ID，对应 t_agent 表的 code',
    token VARCHAR(512) NOT NULL DEFAULT '' COMMENT '认证Token字符串（通常为JWT或随机字符串）',
    expire_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Token过期时间，超过此时间则无效',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Token状态: ACTIVE(有效), EXPIRED(已过期), REVOKED(已撤销)',

    -- 通用字段
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0=未删除, 1=已删除',

    -- 约束
    CONSTRAINT uk_agent_token_code UNIQUE (code)
) COMMENT='Agent认证Token表';

CREATE INDEX IF NOT EXISTS idx_agent_code ON t_agent_auth_token(agent_code,token,expire_time);

-- =============================================
-- 表名: agent
-- 描述: 智能体表，存储用户、内部AI Agent及第三方Agent的基础信息
-- 规范: 所有字段 NOT NULL + 默认值，包含通用字段
-- =============================================
CREATE TABLE IF NOT EXISTS t_agent (
    -- 主键
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',

    -- 业务字段
    code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '业务编码，唯一标识一个Agent，如 agent_001, user_zhang',
    name VARCHAR(200) NOT NULL DEFAULT '' COMMENT 'Agent名称，展示用（昵称或角色名）',
    nickname VARCHAR(200) NOT NULL DEFAULT '' COMMENT 'Agent昵称，用于显示',
    sex tinyint(2) NOT NULL DEFAULT 0 COMMENT '性别: 0=未知, 1=男, 2=女',
    slogan VARCHAR(100) NOT NULL DEFAULT '' COMMENT '个性签名',
    mbti VARCHAR(20) NOT NULL DEFAULT '' COMMENT 'MBTI类型,如: INFJ',
    prompt text NOT NULL DEFAULT '' COMMENT 'Agent的prompt',
    workspace varchar(200) NOT NULL DEFAULT '' COMMENT '工作目录',
    password varchar(200) NOT NULL DEFAULT '' COMMENT '密码',
    email varchar(200) NOT NULL DEFAULT '' COMMENT '邮箱',
    role varchar(200) NOT NULL DEFAULT '' COMMENT '角色',
    description VARCHAR(500) NOT NULL DEFAULT '' COMMENT 'Agent描述，说明角色、职责或个性',
    type VARCHAR(20) NOT NULL DEFAULT 'AGENT' COMMENT '类型: USER(真实用户), AGENT(AI智能体), SYSTEM(系统)',
    avatar VARCHAR(500) NOT NULL DEFAULT '' COMMENT '头像URL或本地路径',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE(活跃), INACTIVE(停用), DELETING(删除中)',

    -- 扩展配置（JSON格式，用于存储第三方Agent集成信息、能力标签等）
    integration_config TEXT NOT NULL DEFAULT ''  COMMENT '集成配置（JSON字符串），示例: {"protocol":"HTTP","endpoint":"https://api.example.com","auth":{"type":"BEARER","token":"xxx"},"capabilities":["text","image"]}',
    llm_config TEXT NOT NULL DEFAULT '' COMMENT 'LLM配置（JSON字符串），示例: {"model":"gpt-3.5-turbo","access_key":"xxx","temperature":0.7,"max_tokens":1024,"top_p":0.9,"frequency_penalty":0.0,"presence_penalty":0.0,"stream":false,"stop":["\\n"],"logprobs":10}',
    -- 可选业务字段（可根据需要增减）
    last_active_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后活跃时间（登录或收发消息时间）',

    -- 通用字段
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0=未删除, 1=已删除',

    -- 约束
    CONSTRAINT uk_agent_code UNIQUE (code)
);

COMMENT ON TABLE t_agent IS '智能体表，支持本地用户、内部AI Agent及第三方Agent的统一管理';

-- 可选索引（根据实际查询场景添加）
CREATE INDEX IF NOT EXISTS idx_agent_type ON t_agent (type, status, is_deleted);
CREATE INDEX IF NOT EXISTS idx_agent_status ON t_agent (status, is_deleted);
CREATE INDEX IF NOT EXISTS idx_agent_last_active ON t_agent (last_active_time DESC);
CREATE INDEX IF NOT EXISTS idx_agent_code ON t_agent (code);
CREATE INDEX IF NOT EXISTS udx_agent_email ON t_agent (email);


-- =============================================
-- 表名: t_chat_group_member
-- 描述: 群组成员关系表
-- =============================================
CREATE TABLE IF NOT EXISTS t_chat_group_member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    chat_group_code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '群组ID，关联t_chat_group.code',
    agent_code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '成员agent.code',
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER' COMMENT '角色: OWNER(群主), ADMIN(管理员), MEMBER(普通成员)',
    nickname_in_group VARCHAR(100) NOT NULL DEFAULT '' COMMENT '群内昵称',
    join_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    status tinyint(2) NOT NULL DEFAULT '0' COMMENT '状态: 0-ACTIVE(正常), 1-QUIT(已退群), 2-KICKED(被踢出)',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0=未删除, 1=已删除',
    CONSTRAINT uk_group_member UNIQUE (chat_group_code, agent_code)
);

COMMENT ON TABLE t_chat_group_member IS '群成员关系表';

CREATE INDEX IF NOT EXISTS idx_group_member_group ON t_chat_group_member (chat_group_code, status, is_deleted);
CREATE INDEX IF NOT EXISTS idx_group_member_agent ON t_chat_group_member (agent_code, status, is_deleted);


-- =============================================
-- 表名: t_chat_group
-- 描述: 群组表，存储群聊基本信息
-- 规范: 表名 t_ 前缀，所有字段 NOT NULL + 默认值，包含通用字段
-- =============================================
CREATE TABLE IF NOT EXISTS t_chat_group (
    -- 主键
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '群组ID',

    -- 业务字段
    code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '群组业务编码，唯一标识，如 group_work_team',
    name VARCHAR(200) NOT NULL DEFAULT '' COMMENT '群组名称',
    avatar VARCHAR(500) NOT NULL DEFAULT '' COMMENT '群头像URL或本地路径',
    owner_code varchar(60) NOT NULL DEFAULT 0 COMMENT '群主 agent.code',
    announcement VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '群公告',
    last_message_code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '最后一条消息的ID（冗余，加速群列表查询）',
    status tinyint(2) NOT NULL DEFAULT '0' COMMENT '状态: 0-ACTIVE(活跃), 1-DISBANDED(已解散)',

    -- 扩展配置（JSON 字符串，存放群级别动态属性）
    ext_config TEXT NOT NULL COMMENT '扩展配置（JSON），示例: {"join_approval": true, "max_members": 500, "allow_invite": false}',

    -- 通用字段
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0=未删除, 1=已删除',

    -- 约束
    CONSTRAINT uk_group_code UNIQUE (code)
);

COMMENT ON TABLE t_chat_group IS '群组基本信息表';

-- 索引
CREATE INDEX IF NOT EXISTS idx_group_owner ON t_chat_group (owner_code, status, is_deleted);
CREATE INDEX IF NOT EXISTS idx_group_status ON t_chat_group (status, is_deleted);
CREATE INDEX IF NOT EXISTS idx_group_code ON t_chat_group (code);


-- =============================================
-- 表名: t_chat_message
-- 描述: 消息表，存储所有聊天消息（单聊和群聊），支持引用回复和@提及
-- 规范: 所有字段 NOT NULL + 默认值，包含通用字段
-- =============================================
CREATE TABLE IF NOT EXISTS t_chat_message (
    -- 主键
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '消息ID',

    code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '消息编码，唯一标识一个消息，如 message_001',

    -- 消息路由字段
    conversation_type VARCHAR(10) NOT NULL DEFAULT 'SINGLE' COMMENT '会话类型: SINGLE(单聊), GROUP(群聊)',
    sender_code VARCHAR(60) NOT NULL DEFAULT '' COMMENT '发送者 agent.id',
    receiver_code VARCHAR(60) NOT NULL DEFAULT '' COMMENT '接收者ID: 单聊时为对方agent.code, 群聊时为group.code',

    -- 消息内容
    message_type VARCHAR(20) NOT NULL DEFAULT 'TEXT' COMMENT '消息类型: TEXT(文本), COMMAND(命令), TASK_RESULT(任务结果),USER(用户消息),AI(AI消息),TOOL(工具信息)',
    content TEXT NOT NULL DEFAULT '' COMMENT '消息内容（文本）',
    content_type TINYINT(4) NOT NULL DEFAULT 0 COMMENT '0-TEXT',
    remark VARCHAR(255) NOT NULL DEFAULT '' COMMENT '备注',

    -- 关联任务系统
    task_id VARCHAR(100) NOT NULL DEFAULT '' COMMENT '关联的任务ID，用于追踪Agent任务',

    -- 会话编码
    conversation_code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '会话编码，用于标识一个会话，如 conversation_001',

    -- 引用回复
    parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '引用的消息ID，0表示无引用',

    -- @提及列表
    mentioned_list TEXT NOT NULL DEFAULT '' COMMENT '@提及的Agent ID列表，JSON数组字符串，如 "[101,102,103]"，空为"[]"',

    -- 消息状态（可选，适合后续扩展）
    status VARCHAR(20) NOT NULL DEFAULT 'SENT' COMMENT '消息状态: SENDING, SENT, DELIVERED, IGNORE-不会计入上下文,FAILED',

    -- 通用字段
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（消息时间戳）',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（如状态变更）',
    is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0=未删除, 1=已删除'
);

COMMENT ON TABLE t_chat_message IS '消息存储表，支持单聊和群聊，纯文本一期，支持引用回复和@提及';


 -- 索引
CREATE INDEX IF NOT EXISTS idx_conversation ON t_chat_message (conversation_type, receiver_code, create_time);
CREATE INDEX IF NOT EXISTS idx_sender ON t_chat_message (sender_code, create_time);
CREATE INDEX IF NOT EXISTS idx_task ON t_chat_message (task_id);
CREATE INDEX IF NOT EXISTS idx_parent ON t_chat_message (parent_id);
CREATE INDEX IF NOT EXISTS idx_status ON t_chat_message (status);
CREATE INDEX IF NOT EXISTS idx_deleted ON t_chat_message (is_deleted);
CREATE INDEX IF NOT EXISTS idx_conversation_code ON t_chat_message (conversation_code);
CREATE UNIQUE INDEX IF NOT EXISTS udx_code ON t_chat_message (code);




-- =============================================
-- 表名: t_contact
-- 描述: 好友关系表，维护用户之间的好友关系
-- 说明: 双向关系各自存一条记录，A添加B为好友时，t_contact中存入(owner=A, friend=B)和(owner=B, friend=A)两条记录
-- =============================================
CREATE TABLE IF NOT EXISTS `t_contact` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `code` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '好友关系编码，唯一标识',
    `owner_code` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '所属用户 Agent Code',
    `friend_code` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '好友的 Agent Code',
    `remark` VARCHAR(200) NOT NULL DEFAULT '' COMMENT '备注名',
    `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE(正常), BLOCKED(已拉黑)',
    `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0=未删除, 1=已删除',
    CONSTRAINT uk_contact_code UNIQUE (`code`)
) COMMENT '好友关系表';

CREATE INDEX IF NOT EXISTS `idx_contact_owner` ON `t_contact` (`owner_code`, `status`, `is_deleted`);
CREATE INDEX IF NOT EXISTS `idx_contact_friend` ON `t_contact` (`friend_code`, `status`, `is_deleted`);
CREATE UNIQUE INDEX IF NOT EXISTS `udx_contact_owner_friend` ON `t_contact` (`owner_code`, `friend_code`);

-- =============================================
-- 表名: t_conversation
-- 描述: 会话表，维护每个 Agent 的最近聊天列表
-- 规范: t_前缀，字段用反引号，所有字段 NOT NULL + 默认值
-- =============================================
CREATE TABLE IF NOT EXISTS `t_conversation` (
    -- 主键
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '会话ID',
    `code` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '会话编码，唯一标识一个会话，如 conversation_001',
    -- 所属用户（这条会话属于哪个 Agent）
    `owner_code` VARCHAR(60) NOT NULL DEFAULT '' COMMENT '所属 Agent ID (t_agent.code)',

    -- 会话对方信息
    `conversation_type` TINYINT(2) NOT NULL DEFAULT '0' COMMENT '会话类型: 0-SINGLE, 1-GROUP',
    `target_code` VARCHAR(60) NOT NULL DEFAULT '' COMMENT '对方ID: 单聊时为对方agent.code, 群聊时为group.code',

    -- 会话摘要（冗余，加速列表展示）
    `last_message_code` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '最后一条消息ID',
    `last_message_content` TEXT NOT NULL DEFAULT '' COMMENT '最后一条消息预览（前200字符）',
    `last_message_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后一条消息时间',
    `last_sender_code` VARCHAR(60) NOT NULL DEFAULT '' COMMENT '最后一条消息发送者ID',

    -- 会话设置（预留）
    `is_top` TINYINT NOT NULL DEFAULT 0 COMMENT '是否置顶: 0=否, 1=是',
    `ext_config` TEXT NOT NULL DEFAULT '' COMMENT '扩展配置（JSON），如免打扰等',

    -- 通用字段
    `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（首次聊天时间）',
    `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0=未删除, 1=已删除'
);

COMMENT ON TABLE t_conversation IS '会话表，存储每个Agent的最近聊天列表';

-- 索引
CREATE INDEX IF NOT EXISTS `idx_conversation_owner_last_time` ON `t_conversation` (`owner_code`, `last_message_time` DESC);
CREATE INDEX IF NOT EXISTS `idx_conversation_owner_top` ON `t_conversation` (`owner_code`, `is_top` DESC, `last_message_time` DESC);
CREATE UNIQUE INDEX IF NOT EXISTS `udx_conversation_code` ON `t_conversation` (`code`);
CREATE UNIQUE INDEX IF NOT EXISTS `udx_conversation_owner_target` ON `t_conversation` (`owner_code`, `target_code`, `conversation_type`);

-- =============================================
-- 表名: t_token_usage
-- 描述: Token用量记录表，追踪每个Agent的Token消耗
-- 规范: t_前缀，字段用反引号，所有字段 NOT NULL + 默认值
-- =============================================
CREATE TABLE IF NOT EXISTS `t_token_usage` (
    -- 主键
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '记录ID',

    -- 业务字段
    `code` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '业务编码，唯一标识一条用量记录',
    `agent_code` varchar(60) NOT NULL DEFAULT '' COMMENT '所属 Agent ID (t_agent.code)',
    `conversation_code` varchar(60) NOT NULL DEFAULT '' COMMENT '关联的会话ID (t_conversation.code)，可为0',
    `message_code` varchar(60) NOT NULL DEFAULT '' COMMENT '关联的消息ID (t_message.code)，可为0',
    `task_id` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '关联的任务ID，用于追踪Agent任务',

    -- Token 用量详情
    `input_tokens` INT NOT NULL DEFAULT 0 COMMENT '输入Token数量（用户消息、提示词等）',
    `output_tokens` INT NOT NULL DEFAULT 0 COMMENT '输出Token数量（Agent回复内容）',
    `total_tokens` INT NOT NULL DEFAULT 0 COMMENT '总Token数量（input+output）',
    `cost` DECIMAL(10, 6) NOT NULL DEFAULT 0.000000 COMMENT '估算费用（美元或自定义单位）',

    -- 使用场景
    `usage_type` VARCHAR(50) NOT NULL DEFAULT 'CHAT' COMMENT '用量类型: CHAT(聊天), TASK(任务), EMBEDDING(向量化), OTHER(其他)',
    `model_name` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '使用的模型名称，如 gpt-4, claude-3, deepseek-chat',
    `provider` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '模型提供商: OPENAI, ANTHROPIC, DEEPSEEK, OLLAMA 等',

    -- 扩展配置
    `ext_config` TEXT NOT NULL DEFAULT '' COMMENT '扩展配置（JSON），如存储原始响应、请求参数等',

    -- 通用字段
    `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（用量产生时间）',
    `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0=未删除, 1=已删除'
);

COMMENT ON TABLE t_token_usage IS 'Token用量记录表，追踪每个Agent的Token消耗';
-- 索引
CREATE INDEX IF NOT EXISTS `idx_token_agent_time` ON `t_token_usage` (`agent_code`, `create_time` DESC);
CREATE INDEX IF NOT EXISTS `idx_token_conversation` ON `t_token_usage` (`conversation_code`);
CREATE INDEX IF NOT EXISTS `idx_token_message` ON `t_token_usage` (`message_code`);
CREATE INDEX IF NOT EXISTS `idx_token_task` ON `t_token_usage` (`task_id`);
CREATE INDEX IF NOT EXISTS `idx_token_type_time` ON `t_token_usage` (`usage_type`, `create_time` DESC);
CREATE INDEX IF NOT EXISTS `idx_token_model` ON `t_token_usage` (`model_name`, `create_time` DESC);
CREATE UNIQUE INDEX IF NOT EXISTS `idx_token_code` on `t_token_usage`(`code`);


-- =============================================
-- 表名: t_memory_summary
-- 描述: L1摘要记忆表，存储对话窗口移出后的增量摘要
-- =============================================
CREATE TABLE IF NOT EXISTS `t_memory_summary` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `code` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '摘要编码，唯一标识',
    `owner_code` VARCHAR(60) NOT NULL DEFAULT '' COMMENT '所属 Agent code',
    `conversation_code` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '所属会话 code',
    `summary_text` TEXT NOT NULL COMMENT '累积摘要文本（Agent第一人称）',
    `start_message_code` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '摘要覆盖的首条消息code',
    `end_message_code` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '摘要覆盖的末条消息code',
    `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
    `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除'
) COMMENT 'L1摘要记忆表';

CREATE INDEX IF NOT EXISTS `idx_summary_owner_conv` ON `t_memory_summary` (`owner_code`, `conversation_code`, `create_time` DESC);
CREATE UNIQUE INDEX IF NOT EXISTS `udx_summary_code` ON `t_memory_summary` (`code`);
CREATE INDEX IF NOT EXISTS `idx_summary_latest` ON `t_memory_summary` (`conversation_code`, `owner_code`, `create_time` DESC);


-- =============================================
-- 表名: t_long_term_fact
-- 描述: L2长期事实表，存储从对话中提取的长期记忆事实
-- =============================================
CREATE TABLE IF NOT EXISTS `t_long_term_fact` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `code` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '事实编码，唯一标识',
    `owner_code` VARCHAR(60) NOT NULL DEFAULT '' COMMENT '发现该事实的 Agent code',
    `target_code` VARCHAR(60) NOT NULL DEFAULT '' COMMENT '事实关联的目标（用户/群组）code',
    `fact_type` VARCHAR(50) NOT NULL DEFAULT '' COMMENT 'PREFERENCE, EVENT, RELATION',
    `fact_content` TEXT NOT NULL COMMENT '事实自然语言描述',
    `importance` FLOAT NOT NULL DEFAULT 0.5 COMMENT '重要性 0-1',
    `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `last_accessed_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后访问时间',
    `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除'
) COMMENT 'L2长期事实表';

CREATE INDEX IF NOT EXISTS `idx_fact_owner_target` ON `t_long_term_fact` (`owner_code`, `target_code`, `importance` DESC);
CREATE UNIQUE INDEX IF NOT EXISTS `udx_fact_code` ON `t_long_term_fact` (`code`);
CREATE INDEX IF NOT EXISTS `idx_fact_last_access` ON `t_long_term_fact` (`last_accessed_time`);

-- =============================================
-- 表名: t_tool
-- 描述: 工具元数据表，存储所有可用工具的元数据
-- =============================================
CREATE TABLE IF NOT EXISTS `t_tool` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `code` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '工具编码，唯一标识',
    `name` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '工具名称（LLM调用用）',
    `description` TEXT NOT NULL COMMENT '工具描述（给LLM看）',
    `parameters_schema` TEXT NOT NULL COMMENT '参数 JSON Schema',
    `source_type` VARCHAR(20) NOT NULL DEFAULT 'LOCAL' COMMENT 'LOCAL, MCP, SCRIPT',
    `source_ref` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '来源引用：LOCAL: beanName.methodName, MCP: serverId:toolName, SCRIPT: scriptId',
    `risk_level` VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL, DANGEROUS, ADMIN',
    `status` VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED, DISABLED',
    `category` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '工具分类',
    `tags` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '标签，逗号分隔',
    `cacheable` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '结果是否可缓存',
    `timeout_ms` INT NOT NULL DEFAULT 10000 COMMENT '超时毫秒',
    `owner_agent_code` VARCHAR(60) NOT NULL DEFAULT '' COMMENT '所属 Agent（空为公共）',
    `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE INDEX `udx_tool_code` (`code`),
    INDEX `idx_tool_source_type` (`source_type`, `status`),
    INDEX `idx_tool_owner` (`owner_agent_code`),
    INDEX `idx_tool_category` (`category`)
) COMMENT '工具元数据表';

-- =============================================
-- 表名: t_tool_audit_log
-- 描述: 工具调用审计日志表
-- =============================================
CREATE TABLE IF NOT EXISTS `t_tool_audit_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `code` VARCHAR(100) NOT NULL DEFAULT '',
    `tool_code` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '工具编码',
    `tool_name` VARCHAR(100) NOT NULL DEFAULT '',
    `agent_code` VARCHAR(60) NOT NULL DEFAULT '',
    `user_code` VARCHAR(60) NOT NULL DEFAULT '',
    `conversation_code` VARCHAR(100) NOT NULL DEFAULT '',
    `request_params` TEXT COMMENT '调用参数（脱敏后）',
    `response_summary` VARCHAR(500) DEFAULT '' COMMENT '结果摘要',
    `status` VARCHAR(20) NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS, FAIL, PENDING, CANCELLED',
    `risk_level` VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    `execution_time_ms` INT DEFAULT 0,
    `error_message` TEXT,
    `trace_id` VARCHAR(100) NOT NULL DEFAULT '',
    `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    INDEX `idx_audit_tool` (`tool_code`, `create_time` DESC),
    INDEX `idx_audit_agent` (`agent_code`, `create_time` DESC),
    INDEX `idx_audit_user` (`user_code`, `create_time` DESC),
    UNIQUE INDEX `udx_audit_code` (`code`)
) COMMENT '工具调用审计日志表';

-- =============================================
-- 表名: t_agent_tool_ref
-- 描述: Agent与工具绑定关系表（多对多），记录每个Agent可使用的工具
-- =============================================
CREATE TABLE IF NOT EXISTS `t_agent_tool_ref` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `agent_code` VARCHAR(100) NOT NULL DEFAULT '' COMMENT 'Agent编码，关联 t_agent.code',
    `tool_code` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '工具编码，关联 t_tool.code',
    `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0=未删除, 1=已删除',
    UNIQUE INDEX `udx_agent_tool` (`agent_code`, `tool_code`),
    INDEX `idx_agent_tool_ref_agent` (`agent_code`),
    INDEX `idx_agent_tool_ref_tool` (`tool_code`)
) COMMENT 'Agent与工具绑定关系表';

-- =============================================
-- 表名: t_task
-- 描述: 通用任务定义表，支持多种任务类型
-- =============================================
CREATE TABLE IF NOT EXISTS `t_task` (
    `id`                BIGINT AUTO_INCREMENT PRIMARY KEY,
    `code`              VARCHAR(64)    NOT NULL COMMENT '任务编码，唯一标识',
    `name`              VARCHAR(128)   NOT NULL COMMENT '任务名称',
    `task_type`         VARCHAR(32)    NOT NULL COMMENT '任务类型: SCHEDULED, MANUAL, AI_COMMAND, WORKFLOW',
    `status`            VARCHAR(16)    NOT NULL DEFAULT 'ENABLED' COMMENT '状态: ENABLED, DISABLED, PAUSED',
    `consumer_key`      VARCHAR(64)    NOT NULL COMMENT '消费者标识，用于路由到具体执行逻辑',

    -- 通用参数（JSON），存放任务特有配置，例如：
    -- 定时任务：{"cron": "0 0 9 * * ?", "timezone": "Asia/Shanghai"}
    -- 手动任务：{}
    -- AI指令：{"prompt": "..."}
    `parameters`        TEXT           NOT NULL COMMENT 'JSON格式的参数，存储任务特有配置',

    -- 归属与元数据（便于管理）
    `agent_code`        VARCHAR(64)    NULL COMMENT '所属Agent编码',
    `conversation_code` VARCHAR(64)    NULL COMMENT '所属会话编码',
    `create_time`       TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`        TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0=未删除, 1=已删除',

    UNIQUE INDEX `uk_task_code` (`code`),
    INDEX `idx_task_type` (`task_type`),
    INDEX `idx_task_status` (`status`),
    INDEX `idx_task_consumer_key` (`consumer_key`),
    INDEX `idx_task_agent_conversation` (`agent_code`, `conversation_code`)
) COMMENT '通用任务定义表';

-- =============================================
-- 表名: t_task_execution_log
-- 描述: 记录每次任务触发的执行情况
-- =============================================
CREATE TABLE IF NOT EXISTS `t_task_execution_log` (
    `id`                BIGINT AUTO_INCREMENT PRIMARY KEY,
    `task_code`         VARCHAR(64)    NOT NULL COMMENT '关联 t_task.code',
    `consumer_key`      VARCHAR(64)    NOT NULL COMMENT '本次执行使用的消费者标识',
    `trigger_type`      VARCHAR(32)    NOT NULL COMMENT '触发类型: SCHEDULED, MANUAL, AI_COMMAND',
    `status`            VARCHAR(16)    NOT NULL COMMENT '执行状态: RUNNING, SUCCESS, FAILED',
    `start_time`        TIMESTAMP      NOT NULL COMMENT '开始时间',
    `end_time`          TIMESTAMP      NULL COMMENT '结束时间',
    `duration_ms`       BIGINT         NULL COMMENT '耗时毫秒',
    `result`            TEXT           NULL COMMENT '执行结果摘要',
    `error_message`     TEXT           NULL COMMENT '错误信息',
    `create_time`       TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`        TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0=未删除, 1=已删除',

    INDEX `idx_exec_log_task_code` (`task_code`),
    INDEX `idx_exec_log_consumer_key` (`consumer_key`),
    INDEX `idx_exec_log_status_time` (`status`, `start_time`)
) COMMENT '任务执行日志表';

-- =============================================
-- 表名: scheduled_tasks
-- 描述: db-scheduler 调度器所需的内部表（由 db-scheduler 自动管理）
-- =============================================

-- =============================================
-- 表名: t_file_record
-- 描述: 文件记录表，存储上传文件的元数据信息（不存 base64）
-- =============================================
CREATE TABLE IF NOT EXISTS `t_file_record` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `code` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '文件编码，唯一标识，用于外部访问',
    `original_name` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '原始文件名',
    `stored_name` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '存储文件名（时间戳_UUID_扩展名）',
    `file_path` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '文件在磁盘上的相对路径',
    `file_extension` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '文件扩展名（含点）',
    `file_size` BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
    `content_type` VARCHAR(200) NOT NULL DEFAULT '' COMMENT 'MIME类型',
    `uploader_code` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '上传者 Agent Code',
    `ref_code` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '关联的业务编码',
    `ref_type` TINYINT(4) NOT NULL DEFAULT 0 COMMENT '0-默认，1-聊天，2-会话',
    `source_type` VARCHAR(50) NOT NULL DEFAULT 'OTHER' COMMENT '文件来源类型: CHAT, TASK, AVATAR, OTHER',
    `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0=未删除, 1=已删除',
    CONSTRAINT uk_file_record_code UNIQUE (`code`)
) COMMENT '文件记录表';

CREATE INDEX IF NOT EXISTS `idx_file_record_uploader` ON `t_file_record` (`uploader_code`, `create_time` DESC);
CREATE INDEX IF NOT EXISTS `idx_file_record_ref` ON `t_file_record` (`ref_code`);
CREATE INDEX IF NOT EXISTS `idx_file_record_source_type` ON `t_file_record` (`source_type`);

create table if not exists scheduled_tasks (
  task_name varchar(100) not null,
  task_instance varchar(100) not null,
  task_data blob,
  execution_time datetime(6) not null,
  picked BOOLEAN not null,
  picked_by varchar(50),
  last_success datetime(6) null,
  last_failure datetime(6) null,
  consecutive_failures INT,
  last_heartbeat datetime(6) null,
  version BIGINT not null,
  priority SMALLINT,
  PRIMARY KEY (task_name, task_instance),
  INDEX execution_time_idx (execution_time),
  INDEX last_heartbeat_idx (last_heartbeat),
  INDEX priority_execution_time_idx (priority desc, execution_time asc)
)
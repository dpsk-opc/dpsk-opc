-- =============================================
-- V1: 初始版本，包含所有表的建表语句
-- =============================================

-- =============================================
-- 表名: t_agent_auth_token
-- 描述: Agent认证Token表，存储Agent的认证Token信息
-- =============================================
CREATE TABLE IF NOT EXISTS t_agent_auth_token (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '业务编码，唯一标识一条Token记录',
    agent_code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '关联的Agent ID，对应 t_agent 表的 code',
    token VARCHAR(512) NOT NULL DEFAULT '' COMMENT '认证Token字符串（通常为JWT或随机字符串）',
    expire_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Token过期时间',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Token状态: ACTIVE(有效), EXPIRED(已过期), REVOKED(已撤销)',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0=未删除, 1=已删除',
    CONSTRAINT uk_agent_token_code UNIQUE (code)
);

CREATE INDEX IF NOT EXISTS idx_agent_code ON t_agent_auth_token(agent_code,token,expire_time);

-- =============================================
-- 表名: t_agent
-- 描述: 智能体表
-- =============================================
CREATE TABLE IF NOT EXISTS t_agent (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '业务编码',
    name VARCHAR(200) NOT NULL DEFAULT '' COMMENT 'Agent名称',
    nickname VARCHAR(200) NOT NULL DEFAULT '' COMMENT 'Agent昵称',
    sex tinyint(2) NOT NULL DEFAULT 0 COMMENT '性别: 0=未知, 1=男, 2=女',
    slogan VARCHAR(100) NOT NULL DEFAULT '' COMMENT '个性签名',
    mbti VARCHAR(20) NOT NULL DEFAULT '' COMMENT 'MBTI类型',
    prompt text NOT NULL DEFAULT '' COMMENT 'Agent的prompt',
    workspace varchar(200) NOT NULL DEFAULT '' COMMENT '工作目录',
    password varchar(200) NOT NULL DEFAULT '' COMMENT '密码',
    email varchar(200) NOT NULL DEFAULT '' COMMENT '邮箱',
    role varchar(200) NOT NULL DEFAULT '' COMMENT '角色',
    description VARCHAR(500) NOT NULL DEFAULT '' COMMENT 'Agent描述',
    type VARCHAR(20) NOT NULL DEFAULT 'AGENT' COMMENT '类型: USER, AGENT, SYSTEM',
    avatar VARCHAR(500) NOT NULL DEFAULT '' COMMENT '头像URL',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE, INACTIVE, DELETING',
    integration_config TEXT NOT NULL DEFAULT '' COMMENT '集成配置（JSON）',
    llm_config TEXT NOT NULL DEFAULT '' COMMENT 'LLM配置（JSON）',
    last_active_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后活跃时间',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    CONSTRAINT uk_agent_code UNIQUE (code)
);

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
    chat_group_code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '群组ID',
    agent_code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '成员agent.code',
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER' COMMENT '角色: OWNER, ADMIN, MEMBER',
    nickname_in_group VARCHAR(100) NOT NULL DEFAULT '' COMMENT '群内昵称',
    join_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    status tinyint(2) NOT NULL DEFAULT '0' COMMENT '状态: 0-ACTIVE, 1-QUIT, 2-KICKED',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    CONSTRAINT uk_group_member UNIQUE (chat_group_code, agent_code)
);

CREATE INDEX IF NOT EXISTS idx_group_member_group ON t_chat_group_member (chat_group_code, status, is_deleted);
CREATE INDEX IF NOT EXISTS idx_group_member_agent ON t_chat_group_member (agent_code, status, is_deleted);

-- =============================================
-- 表名: t_chat_group
-- 描述: 群组表
-- =============================================
CREATE TABLE IF NOT EXISTS t_chat_group (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '群组ID',
    code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '群组业务编码',
    name VARCHAR(200) NOT NULL DEFAULT '' COMMENT '群组名称',
    avatar VARCHAR(500) NOT NULL DEFAULT '' COMMENT '群头像URL',
    owner_code varchar(60) NOT NULL DEFAULT 0 COMMENT '群主 agent.code',
    announcement VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '群公告',
    last_message_code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '最后一条消息ID',
    status tinyint(2) NOT NULL DEFAULT '0' COMMENT '状态: 0-ACTIVE, 1-DISBANDED',
    ext_config TEXT NOT NULL COMMENT '扩展配置（JSON）',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    CONSTRAINT uk_group_code UNIQUE (code)
);

CREATE INDEX IF NOT EXISTS idx_group_owner ON t_chat_group (owner_code, status, is_deleted);
CREATE INDEX IF NOT EXISTS idx_group_status ON t_chat_group (status, is_deleted);
CREATE INDEX IF NOT EXISTS idx_group_code ON t_chat_group (code);

-- =============================================
-- 表名: t_chat_message
-- 描述: 消息表
-- =============================================
CREATE TABLE IF NOT EXISTS t_chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '消息ID',
    code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '消息编码',
    conversation_type VARCHAR(10) NOT NULL DEFAULT 'SINGLE' COMMENT '会话类型: SINGLE, GROUP',
    sender_code VARCHAR(60) NOT NULL DEFAULT '' COMMENT '发送者 agent.id',
    receiver_code VARCHAR(60) NOT NULL DEFAULT '' COMMENT '接收者ID',
    message_type VARCHAR(20) NOT NULL DEFAULT 'TEXT' COMMENT '消息类型',
    content TEXT NOT NULL DEFAULT '' COMMENT '消息内容',
    content_type TINYINT(4) NOT NULL DEFAULT 0 COMMENT '0-TEXT',
    remark VARCHAR(255) NOT NULL DEFAULT '' COMMENT '备注',
    task_id VARCHAR(100) NOT NULL DEFAULT '' COMMENT '关联的任务ID',
    conversation_code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '会话编码',
    relate_user_message_code varchar(100) NOT NULL DEFAULT '' COMMENT '关联的用户消息编码',
    parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '引用的消息ID',
    mentioned_list TEXT NOT NULL DEFAULT '' COMMENT '@提及列表（JSON数组）',
    status VARCHAR(20) NOT NULL DEFAULT 'SENT' COMMENT '消息状态',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除'
);

CREATE INDEX IF NOT EXISTS idx_conversation ON t_chat_message (conversation_type, receiver_code, create_time);
CREATE INDEX IF NOT EXISTS idx_sender ON t_chat_message (sender_code, create_time);
CREATE INDEX IF NOT EXISTS idx_task ON t_chat_message (task_id);
CREATE INDEX IF NOT EXISTS idx_parent ON t_chat_message (parent_id);
CREATE INDEX IF NOT EXISTS idx_status ON t_chat_message (status);
CREATE INDEX IF NOT EXISTS idx_deleted ON t_chat_message (is_deleted);
CREATE INDEX IF NOT EXISTS idx_relate_user_message_code ON t_chat_message (relate_user_message_code);
CREATE INDEX IF NOT EXISTS idx_conversation_code ON t_chat_message (conversation_code);
CREATE UNIQUE INDEX IF NOT EXISTS udx_code ON t_chat_message (code);

-- =============================================
-- 表名: t_contact
-- 描述: 好友关系表
-- =============================================
CREATE TABLE IF NOT EXISTS t_contact (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '好友关系编码',
    owner_code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '所属用户 Agent Code',
    friend_code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '好友的 Agent Code',
    remark VARCHAR(200) NOT NULL DEFAULT '' COMMENT '备注名',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE, BLOCKED',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    CONSTRAINT uk_contact_code UNIQUE (code)
);

CREATE INDEX IF NOT EXISTS idx_contact_owner ON t_contact (owner_code, status, is_deleted);
CREATE INDEX IF NOT EXISTS idx_contact_friend ON t_contact (friend_code, status, is_deleted);
CREATE UNIQUE INDEX IF NOT EXISTS udx_contact_owner_friend ON t_contact (owner_code, friend_code);

-- =============================================
-- 表名: t_conversation
-- 描述: 会话表
-- =============================================
CREATE TABLE IF NOT EXISTS t_conversation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '会话ID',
    code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '会话编码',
    owner_code VARCHAR(60) NOT NULL DEFAULT '' COMMENT '所属 Agent ID',
    conversation_type TINYINT(2) NOT NULL DEFAULT '0' COMMENT '会话类型: 0-SINGLE, 1-GROUP',
    target_code VARCHAR(60) NOT NULL DEFAULT '' COMMENT '对方ID',
    last_message_code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '最后一条消息ID',
    last_message_content TEXT NOT NULL DEFAULT '' COMMENT '最后一条消息预览',
    last_message_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后一条消息时间',
    last_sender_code VARCHAR(60) NOT NULL DEFAULT '' COMMENT '最后一条消息发送者ID',
    last_user_message_code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '最后一条用户消息ID',
    is_top TINYINT NOT NULL DEFAULT 0 COMMENT '是否置顶',
    ext_config TEXT NOT NULL DEFAULT '' COMMENT '扩展配置（JSON）',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除'
);

CREATE INDEX IF NOT EXISTS idx_conversation_owner_last_time ON t_conversation (owner_code, last_message_time DESC);
CREATE INDEX IF NOT EXISTS idx_conversation_owner_top ON t_conversation (owner_code, is_top DESC, last_message_time DESC);
CREATE UNIQUE INDEX IF NOT EXISTS udx_conversation_code ON t_conversation (code);
CREATE UNIQUE INDEX IF NOT EXISTS udx_conversation_owner_target ON t_conversation (owner_code, target_code, conversation_type);

-- =============================================
-- 表名: t_token_usage
-- 描述: Token用量记录表
-- =============================================
CREATE TABLE IF NOT EXISTS t_token_usage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '记录ID',
    code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '业务编码',
    agent_code varchar(60) NOT NULL DEFAULT '' COMMENT '所属 Agent ID',
    conversation_code varchar(60) NOT NULL DEFAULT '' COMMENT '关联的会话ID',
    message_code varchar(60) NOT NULL DEFAULT '' COMMENT '关联的消息ID',
    task_id VARCHAR(100) NOT NULL DEFAULT '' COMMENT '关联的任务ID',
    input_tokens INT NOT NULL DEFAULT 0 COMMENT '输入Token数量',
    output_tokens INT NOT NULL DEFAULT 0 COMMENT '输出Token数量',
    total_tokens INT NOT NULL DEFAULT 0 COMMENT '总Token数量',
    cost DECIMAL(10, 6) NOT NULL DEFAULT 0.000000 COMMENT '估算费用',
    usage_type VARCHAR(50) NOT NULL DEFAULT 'CHAT' COMMENT '用量类型',
    model_name VARCHAR(100) NOT NULL DEFAULT '' COMMENT '使用的模型名称',
    provider VARCHAR(50) NOT NULL DEFAULT '' COMMENT '模型提供商',
    ext_config TEXT NOT NULL DEFAULT '' COMMENT '扩展配置（JSON）',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除'
);

CREATE INDEX IF NOT EXISTS idx_token_agent_time ON t_token_usage (agent_code, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_token_conversation ON t_token_usage (conversation_code);
CREATE INDEX IF NOT EXISTS idx_token_message ON t_token_usage (message_code);
CREATE INDEX IF NOT EXISTS idx_token_task ON t_token_usage (task_id);
CREATE INDEX IF NOT EXISTS idx_token_type_time ON t_token_usage (usage_type, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_token_model ON t_token_usage (model_name, create_time DESC);
CREATE UNIQUE INDEX IF NOT EXISTS idx_token_code on t_token_usage(code);

-- =============================================
-- 表名: t_memory_summary
-- 描述: L1摘要记忆表
-- =============================================
CREATE TABLE IF NOT EXISTS t_memory_summary (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '摘要编码',
    owner_code VARCHAR(60) NOT NULL DEFAULT '' COMMENT '所属 Agent code',
    conversation_code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '所属会话 code',
    summary_text TEXT NOT NULL COMMENT '累积摘要文本',
    start_message_code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '摘要覆盖的首条消息code',
    end_message_code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '摘要覆盖的末条消息code',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除'
);

CREATE INDEX IF NOT EXISTS idx_summary_owner_conv ON t_memory_summary (owner_code, conversation_code, create_time DESC);
CREATE UNIQUE INDEX IF NOT EXISTS udx_summary_code ON t_memory_summary (code);
CREATE INDEX IF NOT EXISTS idx_summary_latest ON t_memory_summary (conversation_code, owner_code, create_time DESC);

-- =============================================
-- 表名: t_long_term_fact
-- 描述: L2长期事实表
-- =============================================
CREATE TABLE IF NOT EXISTS t_long_term_fact (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '事实编码',
    owner_code VARCHAR(60) NOT NULL DEFAULT '' COMMENT '发现该事实的 Agent code',
    target_code VARCHAR(60) NOT NULL DEFAULT '' COMMENT '事实关联的目标code',
    fact_type VARCHAR(50) NOT NULL DEFAULT '' COMMENT 'PREFERENCE, EVENT, RELATION',
    fact_content TEXT NOT NULL COMMENT '事实自然语言描述',
    importance FLOAT NOT NULL DEFAULT 0.5 COMMENT '重要性 0-1',
    status tinyint(3) NOT NULL DEFAULT 0 COMMENT '向量化 0-未向量化, 1-已向量化',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    last_accessed_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后访问时间',
    is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除'
);

CREATE INDEX IF NOT EXISTS idx_fact_owner_target ON t_long_term_fact (owner_code, target_code, importance DESC);
CREATE UNIQUE INDEX IF NOT EXISTS udx_fact_code ON t_long_term_fact (code);
CREATE INDEX IF NOT EXISTS idx_fact_last_access ON t_long_term_fact (last_accessed_time);

-- =============================================
-- 表名: t_tool
-- 描述: 工具元数据表
-- =============================================
CREATE TABLE IF NOT EXISTS t_tool (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '工具编码',
    name VARCHAR(100) NOT NULL DEFAULT '' COMMENT '工具名称',
    description TEXT NOT NULL COMMENT '工具描述',
    parameters_schema TEXT NOT NULL COMMENT '参数 JSON Schema',
    source_type VARCHAR(20) NOT NULL DEFAULT 'LOCAL' COMMENT 'LOCAL, MCP, SCRIPT',
    source_ref VARCHAR(500) NOT NULL DEFAULT '' COMMENT '来源引用',
    risk_level VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL, DANGEROUS, ADMIN',
    status VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED, DISABLED',
    category VARCHAR(100) NOT NULL DEFAULT '' COMMENT '工具分类',
    tags VARCHAR(500) NOT NULL DEFAULT '' COMMENT '标签',
    cacheable TINYINT(1) NOT NULL DEFAULT 0 COMMENT '结果是否可缓存',
    timeout_ms INT NOT NULL DEFAULT 10000 COMMENT '超时毫秒',
    owner_agent_code VARCHAR(60) NOT NULL DEFAULT '' COMMENT '所属 Agent',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE INDEX udx_tool_code (code),
    INDEX idx_tool_source_type (source_type, status),
    INDEX idx_tool_owner (owner_agent_code),
    INDEX idx_tool_category (category)
);

-- =============================================
-- 表名: t_tool_audit_log
-- 描述: 工具调用审计日志表
-- =============================================
CREATE TABLE IF NOT EXISTS t_tool_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(100) NOT NULL DEFAULT '',
    tool_code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '工具编码',
    tool_name VARCHAR(100) NOT NULL DEFAULT '',
    agent_code VARCHAR(60) NOT NULL DEFAULT '',
    user_code VARCHAR(60) NOT NULL DEFAULT '',
    conversation_code VARCHAR(100) NOT NULL DEFAULT '',
    request_params TEXT COMMENT '调用参数（脱敏后）',
    response_summary TEXT DEFAULT '' COMMENT '结果摘要',
    status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS, FAIL, PENDING, CANCELLED',
    risk_level VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    execution_time_ms INT DEFAULT 0,
    error_message TEXT,
    trace_id VARCHAR(100) NOT NULL DEFAULT '',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_audit_tool (tool_code, create_time DESC),
    INDEX idx_audit_agent (agent_code, create_time DESC),
    INDEX idx_audit_user (user_code, create_time DESC),
    UNIQUE INDEX udx_audit_code (code)
);

-- =============================================
-- 表名: t_agent_tool_ref
-- 描述: Agent与工具绑定关系表
-- =============================================
CREATE TABLE IF NOT EXISTS t_agent_tool_ref (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    agent_code VARCHAR(100) NOT NULL DEFAULT '' COMMENT 'Agent编码',
    tool_code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '工具编码',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE INDEX udx_agent_tool (agent_code, tool_code),
    INDEX idx_agent_tool_ref_agent (agent_code),
    INDEX idx_agent_tool_ref_tool (tool_code)
);

-- =============================================
-- 表名: t_task
-- 描述: 通用任务定义表
-- =============================================
CREATE TABLE IF NOT EXISTS t_task (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    code              VARCHAR(64)    NOT NULL DEFAULT '' COMMENT '任务编码',
    name              VARCHAR(128)   NOT NULL DEFAULT '' COMMENT '任务名称',
    task_type         VARCHAR(32)    NOT NULL DEFAULT 'SCHEDULED' COMMENT '任务类型: SCHEDULED, MANUAL, AI_COMMAND, WORKFLOW',
    status            VARCHAR(16)    NOT NULL DEFAULT 'ENABLED' COMMENT '状态: ENABLED, DISABLED, PAUSED',
    consumer_key      VARCHAR(64)    NOT NULL DEFAULT '' COMMENT '消费者标识',
    parameters        TEXT           NOT NULL DEFAULT '' COMMENT 'JSON格式的参数',
    agent_code        VARCHAR(64)    NOT NULL DEFAULT '' COMMENT '所属Agent编码',
    conversation_code VARCHAR(64)    NOT NULL DEFAULT '' COMMENT '所属会话编码',
    source            TINYINT(2)    NOT NULL DEFAULT '1' COMMENT '来源：1-user，2-系统,3-agent',
    create_time       TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted        TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE INDEX uk_task_code (code),
    INDEX idx_task_type (task_type),
    INDEX idx_task_status (status),
    INDEX idx_task_consumer_key (consumer_key),
    INDEX idx_task_agent_conversation (agent_code, conversation_code)
);

-- =============================================
-- 表名: t_task_execution_log
-- 描述: 任务执行日志表
-- =============================================
CREATE TABLE IF NOT EXISTS t_task_execution_log (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_code         VARCHAR(64)    NOT NULL COMMENT '关联 t_task.code',
    consumer_key      VARCHAR(64)    NOT NULL COMMENT '消费者标识',
    trigger_type      VARCHAR(32)    NOT NULL COMMENT '触发类型: SCHEDULED, MANUAL, AI_COMMAND',
    status            VARCHAR(16)    NOT NULL COMMENT '执行状态: RUNNING, SUCCESS, FAILED',
    start_time        TIMESTAMP      NOT NULL COMMENT '开始时间',
    end_time          TIMESTAMP      NULL COMMENT '结束时间',
    duration_ms       BIGINT         NULL COMMENT '耗时毫秒',
    result            TEXT           NULL COMMENT '执行结果摘要',
    error_message     TEXT           NULL COMMENT '错误信息',
    create_time       TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted        TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_exec_log_task_code (task_code),
    INDEX idx_exec_log_consumer_key (consumer_key),
    INDEX idx_exec_log_status_time (status, start_time)
);

-- =============================================
-- 表名: t_file_record
-- 描述: 文件记录表
-- =============================================
CREATE TABLE IF NOT EXISTS t_file_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '文件编码',
    original_name VARCHAR(500) NOT NULL DEFAULT '' COMMENT '原始文件名',
    stored_name VARCHAR(500) NOT NULL DEFAULT '' COMMENT '存储文件名',
    file_path VARCHAR(500) NOT NULL DEFAULT '' COMMENT '文件相对路径',
    file_extension VARCHAR(50) NOT NULL DEFAULT '' COMMENT '文件扩展名',
    file_size BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
    content_type VARCHAR(200) NOT NULL DEFAULT '' COMMENT 'MIME类型',
    uploader_code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '上传者 Agent Code',
    ref_code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '关联的业务编码',
    ref_type TINYINT(4) NOT NULL DEFAULT 0 COMMENT '0-默认，1-聊天，2-会话，3-知识库节点',
    source_type VARCHAR(50) NOT NULL DEFAULT 'OTHER' COMMENT '文件来源类型: CHAT, TASK, AVATAR, OTHER',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    CONSTRAINT uk_file_record_code UNIQUE (code)
);

CREATE INDEX IF NOT EXISTS idx_file_record_uploader ON t_file_record (uploader_code, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_file_record_ref ON t_file_record (ref_code);
CREATE INDEX IF NOT EXISTS idx_file_record_source_type ON t_file_record (source_type);

-- =============================================
-- 表名: t_knowledge_lib
-- 描述: 知识库表
-- =============================================
CREATE TABLE IF NOT EXISTS t_knowledge_lib (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '知识库编码',
    name VARCHAR(200) NOT NULL DEFAULT '' COMMENT '知识库名称',
    description VARCHAR(500) NOT NULL DEFAULT '' COMMENT '知识库描述',
    owner_code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '归属实体编码',
    owner_type TINYINT(4) NOT NULL DEFAULT 0 COMMENT '归属类型: 0-AGENT, 1-GROUP, 2-DISCUSSION',
    status TINYINT(4) NOT NULL DEFAULT 0 COMMENT '状态: 0-UPLOADED, 1-ANALYZING, 2-LEARNED, 3-FAILED',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    CONSTRAINT uk_knowledge_lib_code UNIQUE (code)
);

CREATE INDEX IF NOT EXISTS idx_knowledge_lib_owner ON t_knowledge_lib (owner_code, owner_type, is_deleted);
CREATE INDEX IF NOT EXISTS idx_knowledge_lib_status ON t_knowledge_lib (status, is_deleted);

-- =============================================
-- 表名: t_knowledge_node
-- 描述: 知识库节点表
-- =============================================
CREATE TABLE IF NOT EXISTS t_knowledge_node (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '节点编码',
    lib_code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '所属知识库编码',
    parent_code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '父节点编码',
    name VARCHAR(200) NOT NULL DEFAULT '' COMMENT '节点名称',
    node_type TINYINT(4) NOT NULL DEFAULT 0 COMMENT '节点类型: 0-目录, 1-文件',
    level INT NOT NULL DEFAULT 0 COMMENT '层级深度',
    file_code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '关联的文件编码',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序号',
    status TINYINT(4) NOT NULL DEFAULT 0 COMMENT '状态: 0-UPLOADED, 1-ANALYZING, 2-LEARNED, 3-FAILED',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    CONSTRAINT uk_knowledge_node_code UNIQUE (code)
);

CREATE INDEX IF NOT EXISTS idx_knowledge_node_lib ON t_knowledge_node (lib_code, is_deleted);
CREATE INDEX IF NOT EXISTS idx_knowledge_node_parent ON t_knowledge_node (parent_code, sort_order, is_deleted);
CREATE INDEX IF NOT EXISTS idx_knowledge_node_file ON t_knowledge_node (file_code);

-- =============================================
-- 表名: scheduled_tasks
-- 描述: db-scheduler 调度器内部表
-- =============================================
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
);

-- =============================================
-- 表名: t_mcp_template
-- 描述: MCP 服务模板表
-- =============================================
CREATE TABLE IF NOT EXISTS t_mcp_template (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    code              VARCHAR(100) NOT NULL DEFAULT '' COMMENT '业务编码',
    name              VARCHAR(128) NOT NULL DEFAULT '' COMMENT '模板名称',
    description       VARCHAR(512) NOT NULL DEFAULT '' COMMENT '模板描述',
    command           VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '启动命令: npx/node/python/uvx/go/docker',
    args              JSON         NOT NULL DEFAULT '[]' COMMENT '命令参数列表',
    runtime_env       TINYINT(2)   NOT NULL DEFAULT 0 COMMENT '执行路由: 0-none, 1-electron, 2-backend',
    runtime_available TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '环境是否可用',
    tools             JSON         NOT NULL DEFAULT '[]' COMMENT '工具列表快照',
    create_time       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    CONSTRAINT uk_mcp_template_code UNIQUE (code)
);

CREATE INDEX IF NOT EXISTS idx_mcp_template_name ON t_mcp_template (name, is_deleted);
CREATE INDEX IF NOT EXISTS idx_mcp_template_runtime_env ON t_mcp_template (runtime_env, is_deleted);

-- =============================================
-- 表名: t_agent_mcp_binding
-- 描述: Agent MCP 绑定表
-- =============================================
CREATE TABLE IF NOT EXISTS t_agent_mcp_binding (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    code           VARCHAR(100) NOT NULL DEFAULT '' COMMENT '业务编码',
    template_code  VARCHAR(100) NOT NULL DEFAULT '' COMMENT '关联模板编码',
    agent_code     VARCHAR(100) NOT NULL DEFAULT '' COMMENT '绑定的Agent编码',
    enabled        TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    env_vars       TEXT         NOT NULL DEFAULT '' COMMENT 'Agent专属环境变量',
    status         TINYINT(2)   NOT NULL DEFAULT 0 COMMENT '运行状态: 0-stopped, 1-running, 2-connecting',
    tools_snapshot JSON         NOT NULL DEFAULT '[]' COMMENT '绑定时的工具快照',
    create_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    CONSTRAINT uk_mcp_binding_agent_template UNIQUE (agent_code, template_code)
);

CREATE INDEX IF NOT EXISTS idx_mcp_binding_template ON t_agent_mcp_binding (template_code, is_deleted);
CREATE INDEX IF NOT EXISTS idx_mcp_binding_agent ON t_agent_mcp_binding (agent_code, is_deleted);
CREATE INDEX IF NOT EXISTS idx_mcp_binding_status ON t_agent_mcp_binding (status, is_deleted);


-- =============================================
-- 表名: t_todo_item
-- 描述: 待办事项表，存储用户创建的待办任务
-- =============================================
CREATE TABLE IF NOT EXISTS t_todo_item (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    code          VARCHAR(100)  NOT NULL DEFAULT '' COMMENT '待办编码，唯一标识',
    agent_code    VARCHAR(100)  NOT NULL DEFAULT '' COMMENT '关联的联系人 Agent Code',
    owner_code    VARCHAR(100)  NOT NULL DEFAULT '' COMMENT '所属用户 Agent Code（当前登录用户）',
    title         VARCHAR(500)  NOT NULL DEFAULT '' COMMENT '待办名称',
    content       TEXT          NOT NULL COMMENT '待办内容',
    due_time      TIMESTAMP     NULL COMMENT '逾期时间',
    alarm_sound   VARCHAR(500)  NULL COMMENT '闹铃文件URL，NULL=默认铃声',
    alarm_enabled TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否开启提醒: 0=关闭, 1=开启',
    task_code     VARCHAR(100)  NOT NULL DEFAULT '' COMMENT '关联的任务编码',
    conversation_code VARCHAR(100)  NOT NULL DEFAULT '' COMMENT '关联的会话编码',
    status        TINYINT(2)    NOT NULL DEFAULT 0 COMMENT '状态: 0=pending(待办), 1=in_progress(进行中), 2=done(已完成)',
    create_time   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted    TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0=未删除, 1=已删除',
    CONSTRAINT uk_todo_item_code UNIQUE (code)
);

CREATE INDEX IF NOT EXISTS idx_todo_item_agent ON t_todo_item (agent_code, is_deleted);
CREATE INDEX IF NOT EXISTS idx_todo_item_owner ON t_todo_item (owner_code, is_deleted);
CREATE INDEX IF NOT EXISTS idx_todo_item_status ON t_todo_item (status, is_deleted);
CREATE INDEX IF NOT EXISTS idx_todo_item_due_time ON t_todo_item (due_time, is_deleted);
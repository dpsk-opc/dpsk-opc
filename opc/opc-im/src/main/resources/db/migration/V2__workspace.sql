-- =============================================
-- 工作空间与文件访问控制（P1）
--   1. t_chat_group 新增 workspace（群公共产出目录）
--   2. t_tool 新增 filesystem_access / path_params（路径声明）
--
-- 注意：数据库为 H2 2.2，语法要求：
--   * 不支持 ALTER TABLE ... ADD COLUMN IF NOT EXISTS（MySQL 语法）
--   * 不支持在 ALTER TABLE 中写 COMMENT
--   本脚本由 Flyway 版本管理，只会执行一次，无需 IF NOT EXISTS。
-- =============================================

-- 群工作空间（公共产出目录）：全员可见可写，不按成员划分子目录
ALTER TABLE t_chat_group ADD COLUMN workspace varchar(500) DEFAULT '' NOT NULL;

-- 工具文件系统访问能力位：NONE（不碰文件系统，跳过路径校验）/ READ / WRITE
ALTER TABLE t_tool ADD COLUMN filesystem_access varchar(20) DEFAULT 'NONE' NOT NULL;

-- 路径参数声明（JSON 数组，元素形如 {"name":"path","direction":"WRITE","kind":"FILE"}）
ALTER TABLE t_tool ADD COLUMN path_params varchar(4000) DEFAULT '' NOT NULL;

CREATE INDEX IF NOT EXISTS idx_tool_filesystem_access ON t_tool (filesystem_access);

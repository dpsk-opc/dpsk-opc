package com.xiaomizhou.dpsk.rag;

/**
 * RAG 命名空间枚举，用于物理隔离不同领域的数据。
 * <p>
 * 每个 namespace 对应独立的向量存储文件，互不影响。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/21
 */
public enum RagNamespace {

    /** L2 语义记忆 */
    MEMORY("memory"),

    /** L3 知识库 */
    KNOWLEDGE("knowledge"),

    /** 工具检索 */
    TOOL("tool"),

    /** Skill 检索 */
    SKILL("skill");

    private final String dirName;

    RagNamespace(String dirName) {
        this.dirName = dirName;
    }

    public String getDirName() {
        return dirName;
    }
}

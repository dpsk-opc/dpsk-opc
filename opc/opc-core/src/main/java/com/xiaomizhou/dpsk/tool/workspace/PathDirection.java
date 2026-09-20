package com.xiaomizhou.dpsk.tool.workspace;

/**
 * 路径访问方向。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/9/20
 */
public enum PathDirection {

    /** 读取 */
    READ,

    /** 写入 / 修改 */
    WRITE,

    /** 删除 */
    DELETE,

    /** 列出目录 */
    LIST;

    /**
     * 是否为写类操作（写入 / 删除），写类操作在确认交互与高危判定上更严格。
     */
    public boolean isWriteLike() {
        return this == WRITE || this == DELETE;
    }
}

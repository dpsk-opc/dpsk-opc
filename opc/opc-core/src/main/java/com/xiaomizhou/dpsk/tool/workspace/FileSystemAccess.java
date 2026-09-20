package com.xiaomizhou.dpsk.tool.workspace;

/**
 * 工具的文件系统访问能力位。
 * <p>
 * 判定依据是"是否发生文件系统副作用/读取"，而不是"参数里有没有像路径的字符串"，
 * 用于避免大量误报。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/9/20
 */
public enum FileSystemAccess {

    /**
     * 不碰文件系统 —— <b>完全跳过路径校验</b>（默认值，保持存量工具零影响）。
     */
    NONE,

    /**
     * 读取文件 / 目录。
     */
    READ,

    /**
     * 写入 / 修改 / 删除。
     */
    WRITE
}

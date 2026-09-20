package com.xiaomizhou.dpsk.tool.workspace;

/**
 * 路径参数的类型，决定校验语义。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/9/20
 */
public enum PathKind {

    /** 单个文件 */
    FILE,

    /** 目录 */
    DIR,

    /** 通配模式（如 *.log、src/**），按最长公共前缀目录判定 */
    GLOB;
}

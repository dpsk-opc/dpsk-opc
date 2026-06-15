package com.xiaomizhou.dpsk.constant;

/**
 * 知识库节点类型常量
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/13
 */
public interface KnowledgeNodeType {

    /** 目录节点 */
    int FOLDER = 0;

    /** 文件节点（叶子节点，不可再挂子节点） */
    int FILE = 1;

}

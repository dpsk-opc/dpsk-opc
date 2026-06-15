package com.xiaomizhou.dpsk.constant;

/**
 * 知识库状态常量
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/13
 */
public interface KnowledgeLibStatus {

    /** 已上传 - 文件上传完成，等待分析 */
    int UPLOADED = 0;

    /** 分析中 - 正在解析/切分/向量化 */
    int ANALYZING = 1;

    /** 已学习 - 分析完成，知识库可用 */
    int LEARNED = 2;

    /** 失败 - 分析失败 */
    int FAILED = 3;

    /**
     * 不支持 - 知识库不支持，无法分析
     */
    int UNSUPPORT = 4;
}

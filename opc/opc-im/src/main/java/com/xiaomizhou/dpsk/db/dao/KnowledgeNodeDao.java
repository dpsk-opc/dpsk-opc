package com.xiaomizhou.dpsk.db.dao;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaomizhou.dpsk.db.mapper.KnowledgeNodeMapper;
import com.xiaomizhou.dpsk.db.model.KnowledgeNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 知识库节点 DAO
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/13
 */
@Component
@Slf4j
public class KnowledgeNodeDao extends ServiceImpl<KnowledgeNodeMapper, KnowledgeNode> {

    /**
     * 根据编码查询节点
     */
    public KnowledgeNode getByCode(String code) {
        return getOne(Wrappers.<KnowledgeNode>lambdaQuery().eq(KnowledgeNode::getCode, code));
    }

    /**
     * 查询指定父节点下的子节点列表，按 sort_order 升序
     */
    public List<KnowledgeNode> listByParentCode(String parentCode, String libCode) {
        if(StringUtils.isBlank(libCode)){
            return Collections.emptyList();
        }
        return list(Wrappers.<KnowledgeNode>lambdaQuery()
                .eq(KnowledgeNode::getParentCode, parentCode)
                .eq(KnowledgeNode::getLibCode, libCode)
                .orderByAsc(KnowledgeNode::getSortOrder));
    }

    /**
     * 查询指定父节点下的所有子孙节点（递归查询所有后代）
     * 用于级联删除目录时，找到所有子节点
     *
     * @param parentCode 父节点编码
     * @return 所有子孙节点列表
     */
    public List<KnowledgeNode> listDescendants(String parentCode, String libCode) {
        // 先查直接子节点
        List<KnowledgeNode> children = listByParentCode(parentCode, libCode);
        List<KnowledgeNode> all = new java.util.ArrayList<>(children);
        // 递归查每个子节点的后代
        for (KnowledgeNode child : children) {
            if (child.getNodeType() == 0) { // 目录节点才需要递归
                all.addAll(listDescendants(child.getCode(), libCode));
            }
        }
        return all;
    }

    /**
     * 根据知识库编码查询所有节点
     */
    public List<KnowledgeNode> listByLibCode(String libCode) {
        return list(Wrappers.<KnowledgeNode>lambdaQuery()
                .eq(KnowledgeNode::getLibCode, libCode)
                .orderByAsc(KnowledgeNode::getSortOrder));
    }

    /**
     * 查询指定知识库下、指定状态的文件节点（用于知识构建任务捞取待处理文件）
     * @param status  节点状态: 0-已上传, 1-分析中, 2-已学习, 3-失败
     * @return 文件节点列表
     */
    public List<KnowledgeNode> listFileNodesByStatus(int status, int limit) {
        return list(Wrappers.<KnowledgeNode>lambdaQuery()
//                .eq(KnowledgeNode::getLibCode, libCode)
                .eq(KnowledgeNode::getNodeType, 1)  // FILE
                .eq(KnowledgeNode::getStatus, status)
                .orderByAsc(KnowledgeNode::getSortOrder).last("limit " + limit));
    }

    /**
     * CAS 更新节点状态（乐观锁：只有当前状态等于 expectedStatus 时才更新）
     *
     * @param nodeCode       节点编码
     * @param expectedStatus 期望的当前状态
     * @param newStatus      目标新状态
     * @return 是否更新成功
     */
    public boolean casUpdateStatus(String nodeCode, int expectedStatus, int newStatus) {
        return update(Wrappers.<KnowledgeNode>lambdaUpdate()
                .set(KnowledgeNode::getStatus, newStatus)
                .set(KnowledgeNode::getUpdateTime, new java.util.Date())
                .eq(KnowledgeNode::getCode, nodeCode)
                .eq(KnowledgeNode::getStatus, expectedStatus));
    }

}

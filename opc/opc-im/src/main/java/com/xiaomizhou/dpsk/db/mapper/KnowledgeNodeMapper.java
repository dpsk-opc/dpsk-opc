package com.xiaomizhou.dpsk.db.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaomizhou.dpsk.db.model.KnowledgeNode;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 知识库节点 Mapper
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/13
 */
public interface KnowledgeNodeMapper extends BaseMapper<KnowledgeNode> {

    @Select("<script> select parent_code as code,count(*) as count from t_knowledge_node where parent_code in " +
            "<foreach collection='parentNodeCodes' item='item' open='(' close=')' separator=','>#{item}</foreach>" +
            "group by parent_code" +
            "</script>")
    List<Map<String,Integer>> getNodeCountByLibCode(@Param("parentNodeCodes") List<String> parentNodeCodes);
}

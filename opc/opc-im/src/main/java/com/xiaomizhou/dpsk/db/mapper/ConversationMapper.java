package com.xiaomizhou.dpsk.db.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaomizhou.dpsk.db.model.Conversation;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/20 12:39
 * @description
 */
public interface ConversationMapper extends BaseMapper<Conversation> {

    /**
     * 物理删除会话（手写 SQL，绕过 @TableLogic，避免唯一索引占位冲突）
     *
     * @param id 会话主键
     * @return 影响行数
     */
    @Delete("DELETE FROM t_conversation WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);

    /**
     * 物理删除指定类型 + target 的会话（如解散群聊时清理群会话）
     *
     * @param conversationType 会话类型
     * @param targetCode       目标编码
     * @return 影响行数
     */
    @Delete("DELETE FROM t_conversation WHERE conversation_type = #{conversationType} AND target_code = #{targetCode}")
    int physicalDeleteByTypeAndTarget(@Param("conversationType") Integer conversationType,
                                      @Param("targetCode") String targetCode);

}

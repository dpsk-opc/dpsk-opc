package com.xiaomizhou.dpsk.db.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaomizhou.dpsk.db.model.ChatGroupMember;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/18 18:33
 * @description
 */
public interface ChatGroupMemberMapper extends BaseMapper<ChatGroupMember> {

    /**
     * 物理删除某群的全部成员关系（手写 SQL，绕过 @TableLogic）
     *
     * @param groupCode 群编码
     * @return 影响行数
     */
    @Delete("DELETE FROM t_chat_group_member WHERE chat_group_code = #{groupCode}")
    int physicalDeleteByGroupCode(@Param("groupCode") String groupCode);

    /**
     * 物理删除指定 id 的成员关系
     *
     * @param id 成员关系主键
     * @return 影响行数
     */
    @Delete("DELETE FROM t_chat_group_member WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);

}

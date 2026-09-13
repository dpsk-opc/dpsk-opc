package com.xiaomizhou.dpsk.db.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaomizhou.dpsk.db.model.ChatGroup;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/18 18:33
 * @description
 */
public interface GroupMapper extends BaseMapper<ChatGroup> {

    /**
     * 物理删除群（手写 SQL，绕过 @TableLogic）
     *
     * @param groupCode 群编码
     * @return 影响行数
     */
    @Delete("DELETE FROM t_chat_group WHERE code = #{groupCode}")
    int physicalDeleteByCode(@Param("groupCode") String groupCode);

}

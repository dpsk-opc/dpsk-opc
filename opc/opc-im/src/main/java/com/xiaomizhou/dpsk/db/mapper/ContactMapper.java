package com.xiaomizhou.dpsk.db.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaomizhou.dpsk.db.model.Contact;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/6/11
 */
public interface ContactMapper extends BaseMapper<Contact> {

    /**
     * 物理删除单向好友关系（手写 SQL，绕过 @TableLogic 逻辑删除，避免唯一索引占位冲突）
     *
     * @param ownerCode  用户 code
     * @param friendCode 好友 code
     * @return 影响行数
     */
    @Delete("DELETE FROM t_contact WHERE owner_code = #{ownerCode} AND friend_code = #{friendCode}")
    int physicalDeleteByOwnerAndFriend(@Param("ownerCode") String ownerCode, @Param("friendCode") String friendCode);

    /**
     * 物理删除某用户作为 owner 的全部好友关系
     *
     * @param ownerCode 用户 code
     * @return 影响行数
     */
    @Delete("DELETE FROM t_contact WHERE owner_code = #{ownerCode}")
    int physicalDeleteByOwnerCode(@Param("ownerCode") String ownerCode);

    /**
     * 物理删除所有把某用户作为 friend 的好友关系
     *
     * @param friendCode 好友 code
     * @return 影响行数
     */
    @Delete("DELETE FROM t_contact WHERE friend_code = #{friendCode}")
    int physicalDeleteByFriendCode(@Param("friendCode") String friendCode);

}

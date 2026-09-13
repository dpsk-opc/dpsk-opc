package com.xiaomizhou.dpsk.db.dao;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaomizhou.dpsk.db.mapper.ChatGroupMemberMapper;
import com.xiaomizhou.dpsk.db.model.ChatGroupMember;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/18 18:33
 * @description
 */
@Component
@Slf4j
public class ChatGroupMemberDao extends ServiceImpl<ChatGroupMemberMapper, ChatGroupMember> {

    /**
     * 物理删除某群的全部成员关系（手写 SQL，绕过 @TableLogic，避免唯一索引 uk_group_member 占位）
     *
     * @param groupCode 群编码
     * @return 影响行数
     */
    public int physicalDeleteByGroupCode(String groupCode) {
        if (StringUtils.isBlank(groupCode)) {
            return 0;
        }
        return baseMapper.physicalDeleteByGroupCode(groupCode);
    }

    /**
     * 物理删除指定 id 的成员关系
     *
     * @param id 成员关系主键
     * @return 是否删除成功
     */
    public boolean physicalDeleteById(Long id) {
        if (Objects.isNull(id)) {
            return false;
        }
        return baseMapper.physicalDeleteById(id) > 0;
    }

}

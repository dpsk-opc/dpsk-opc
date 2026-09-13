package com.xiaomizhou.dpsk.db.dao;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaomizhou.dpsk.db.mapper.GroupMapper;
import com.xiaomizhou.dpsk.db.model.ChatGroup;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/18 18:33
 * @description
 */
@Component
@Slf4j
public class ChatGroupDao extends ServiceImpl<GroupMapper, ChatGroup> {


    public ChatGroup getByCode(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        return getOne(Wrappers.<ChatGroup>lambdaQuery().eq(ChatGroup::getCode, code));
    }

    /**
     * 物理删除群（手写 SQL，绕过 @TableLogic，避免唯一索引 uk_group_code 占位）
     *
     * @param groupCode 群编码
     * @return 是否删除成功
     */
    public boolean physicalDeleteByCode(String groupCode) {
        if (StringUtils.isBlank(groupCode)) {
            return false;
        }
        return baseMapper.physicalDeleteByCode(groupCode) > 0;
    }

}

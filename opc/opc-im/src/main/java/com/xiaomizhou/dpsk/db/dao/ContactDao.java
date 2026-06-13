package com.xiaomizhou.dpsk.db.dao;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaomizhou.dpsk.db.mapper.ContactMapper;
import com.xiaomizhou.dpsk.db.model.Contact;
import com.xiaomizhou.dpsk.utils.SequenceUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 好友关系 DAO
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/11
 */
@Component
@Slf4j
public class ContactDao extends ServiceImpl<ContactMapper, Contact> {

    /**
     * 查询用户的所有好友 code 列表
     *
     * @param ownerCode 用户 code
     * @return 好友 code 列表
     */
    public List<String> listFriendCodes(String ownerCode) {
        if (StringUtils.isBlank(ownerCode)) {
            return Collections.emptyList();
        }
        return lambdaQuery()
                .eq(Contact::getOwnerCode, ownerCode)
                .eq(Contact::getStatus, "ACTIVE")
                .list()
                .stream()
                .map(Contact::getFriendCode)
                .collect(Collectors.toList());
    }

    /**
     * 根据 ownerCode 删除其所有好友关系
     *
     * @param ownerCode 用户 code
     */
    public void deleteByOwnerCode(String ownerCode) {
        if (StringUtils.isBlank(ownerCode)) {
            return;
        }
        remove(Wrappers.<Contact>lambdaUpdate().eq(Contact::getOwnerCode, ownerCode));
    }

    /**
     * 根据 friendCode 删除所有将其标记为好友的关系
     *
     * @param friendCode 好友 code
     */
    public void deleteByFriendCode(String friendCode) {
        if (StringUtils.isBlank(friendCode)) {
            return;
        }
        remove(Wrappers.<Contact>lambdaUpdate().eq(Contact::getFriendCode, friendCode));
    }

    /**
     * 判断是否为好友关系
     *
     * @param ownerCode  用户 code
     * @param friendCode 好友 code
     * @return true 是好友
     */
    public boolean isFriend(String ownerCode, String friendCode) {
        if (StringUtils.isAnyBlank(ownerCode, friendCode)) {
            return false;
        }
        return lambdaQuery()
                .eq(Contact::getOwnerCode, ownerCode)
                .eq(Contact::getFriendCode, friendCode)
                .eq(Contact::getStatus, "ACTIVE")
                .count() > 0;
    }

    /**
     * 添加好友关系（双向：新用户与已有用户的互加好友）
     * 将 newUserCode 与 existingUserCode 互相设为好友
     *
     * @param newUserCode      新用户 code
     * @param existingUserCode 已有用户 code
     */
    public void addFriendRelation(String newUserCode, String existingUserCode) {
        if (StringUtils.isAnyBlank(newUserCode, existingUserCode)) {
            return;
        }
        if (newUserCode.equals(existingUserCode)) {
            return;
        }

        Date now = new Date();
        String contactPrefix = "CON";

        // 新用户 -> 已有用户
        Contact c1 = new Contact();
        c1.setCode(SequenceUtils.generator().next(contactPrefix));
        c1.setOwnerCode(newUserCode);
        c1.setFriendCode(existingUserCode);
        c1.setStatus("ACTIVE");
        c1.setCreateTime(now);
        c1.setUpdateTime(now);
        save(c1);

        // 已有用户 -> 新用户
        Contact c2 = new Contact();
        c2.setCode(SequenceUtils.generator().next(contactPrefix));
        c2.setOwnerCode(existingUserCode);
        c2.setFriendCode(newUserCode);
        c2.setStatus("ACTIVE");
        c2.setCreateTime(now);
        c2.setUpdateTime(now);
        save(c2);
    }

}

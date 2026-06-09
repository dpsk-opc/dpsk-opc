package com.xiaomizhou.dpsk.db.dao;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaomizhou.dpsk.db.mapper.UsageMapper;
import com.xiaomizhou.dpsk.db.model.TokenUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/20 14:38
 * @description
 */
@RequiredArgsConstructor
@Component
@Slf4j
public class TokenUsageDao extends ServiceImpl<UsageMapper, TokenUsage> {


    /**
     *
     * @param msgCode
     * @return
     */
    public TokenUsage getOneByMsgCode(String msgCode) {
        if (StringUtils.isBlank(msgCode)) {
            return null;
        }

        return getOne(Wrappers.<TokenUsage>lambdaQuery()
                .eq(TokenUsage::getMessageCode, msgCode));
    }

}

package com.xiaomizhou.dpsk.db.dto;

import lombok.Data;

import java.util.List;

/**
 * 移除群组成员请求参数
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/9/13
 */
@Data
public class GroupRemoveMemberCmd {

    /**
     * 群组编码
     */
    private String groupCode;

    /**
     * 待移除的成员编码列表
     */
    private List<String> memberCodes;

}

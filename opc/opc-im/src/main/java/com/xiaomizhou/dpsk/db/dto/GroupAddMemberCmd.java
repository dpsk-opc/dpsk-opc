package com.xiaomizhou.dpsk.db.dto;

import lombok.Data;

import java.util.List;

/**
 * 添加群组成员请求参数
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/21
 */
@Data
public class GroupAddMemberCmd {

    /**
     * 群组编码
     */
    private String groupCode;

    /**
     * 成员编码列表
     */
    private List<String> memberCodes;

}

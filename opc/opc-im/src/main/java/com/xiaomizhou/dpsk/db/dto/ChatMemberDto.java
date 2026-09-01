package com.xiaomizhou.dpsk.db.dto;

import lombok.Data;

/**
 * Agent 数据传输对象
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/18
 */
@Data
public class ChatMemberDto {

    /**
     * 业务编码
     */
    private String code;

    /**
     * 名称
     */
    private String name;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 角色: OWNER, ADMIN, MEMBER
     */
    private String roleInGroup;

    /**
     * 类型: USER(真实用户), AGENT-LOCAL(AI智能体), SYSTEM(系统), AGENT-A2A(远程AI智能体)
     */
    private String type;

    /**
     * 头像
     */
    private String avatar;

}

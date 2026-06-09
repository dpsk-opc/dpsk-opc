package com.xiaomizhou.dpsk.db.dto;

import lombok.Data;

import java.util.Date;

/**
 * 群组数据传输对象
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/22
 */
@Data
public class ChatGroupDto {

    /**
     * 群组编码
     */
    private String code;

    /**
     * 群组名称
     */
    private String name;

    /**
     * 群组头像
     */
    private String avatar;

    /**
     * 群主编码
     */
    private String ownerCode;

    /**
     * 群公告
     */
    private String announcement;

    /**
     * 状态: 0=ACTIVE, 1=DISBANDED
     */
    private Integer status;

    /**
     * 最后一条消息编码
     */
    private String lastMessageCode;

    /**
     * 扩展配置
     */
    private String extConfig;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 成员数量
     */
    private Long memberCount;

}

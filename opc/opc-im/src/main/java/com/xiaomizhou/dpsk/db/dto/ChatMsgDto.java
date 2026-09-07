package com.xiaomizhou.dpsk.db.dto;

import com.xiaomizhou.dpsk.constant.ConversationType;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/22 11:28
 * @description
 */
@Data
@Builder
public class ChatMsgDto {

    /**
     * 目标id，单聊就是userId，群聊就是groupId
     */
    private String targetId;

    /**
     * 发送者id
     */
    private String sendId;

    /**
     * 对话类型，单聊还是群聊
     */
    private Integer conversationType;

    /**
     * 会话编码
     */
    private String conversationCode;

    /**
     * 消息内容
     */
    private String message;

    /**
     * 消息类型，TEXT, COMMAND, TASK_RESULT
     */
    private String messageType;

    /**
     * 引用的消息ID，0表示无引用
     */
    private String parentMsgCode;

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 提及的Agent ID列表
     */
    private List<String> mentionedList;

    /**
     * 文件编码列表
     */
    private List<String> fileCodes;

    /**
     * MCP 编码列表
     */
    private List<String> mcpCodes;

    /**
     * 技能路径列表
     */
    private List<String> skillPaths;

    /**
     * 消息状态
     */
    private String status;

    private ImageGenerateDto imageParam;


    @Data
    public static class ImageGenerateDto {

        /**
         * 0-生成图片，1-生成视频，注意：mode = 1图生图模式时,fileCodes是图片url列表
         *
         */
        private int mode = 0;

        /**
         * 图片大小，默认1024 * 768
         */
        private String size = "1024*768";

        private String format = "PNG";     // PNG

        /**
         * 生成图片数量，默认1
         */
        private int count = 1;

        private List<String> urls;

    }

}

package com.xiaomizhou.dpsk.agent;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Agent 构建规范，由 opc-im 组装后传给 opc-core。
 * 所有聊天模式（single/group/workflow）统一使用此输入格式。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/3
 */
@Data
@Builder(toBuilder = true)
public class AgentBuildSpec {

    /** 单聊模式 */
    public static final String MODE_SINGLE = "SINGLE";

    /** 群聊模式 */
    public static final String MODE_GROUP = "GROUP";

    /** 工作流模式 */
    public static final String MODE_WORKFLOW = "WORKFLOW";

    /** 图片模式 */
    public static final String MODE_IMAGE = "IMAGE";

    /** 运行模式：SINGLE / GROUP / WORKFLOW */
    private String mode;

    /** 会话编码 */
    private String conversationCode;

    /** 当前用户编码 */
    private String userCode;

    /** 用户当前消息内容 */
    private String userContent;

    /**
     * 本次触发执行的用户消息编码（即用户"原始需求"所在的消息）。
     * <p>
     * 当窗口因工具消息过多把 UserMessage 挤出时，L0 记忆会按此 code 精确取回该条真实用户消息，
     * 锚定在窗口内，避免模型遗忘用户的原始需求。单聊/群聊由调用方从当次用户消息填充；
     * 无真实用户消息的场景（如定时任务、部分工作流）可为空，为空时走原有兜底。
     */
    private String userMessageCode;

    /** 目标 Agent（单聊时使用） */
    private String targetAgentCode;

    /** 目标 Agent 列表（群聊时使用，含 Agent 编码） */
    private List<String> targetAgentCodes;

    /** 群组编码（群聊时使用） */
    private String groupCode;

    /** 引用消息编码（@ 功能） */
    private String quoteMessageCode;

    /**
     * 被@的 Agent code 集合（群聊时使用，已由 opc-im 从 mentionedList 解析出）。
     * 被@的 agent 必须全部对本次消息做出回应。
     */
    private List<String> mentionedAgentCodes;

    /**
     * 群聊上下文：最近 N 条消息（含 sender），用于「衔接判断」与「能力匹配」挑选发言 agent。
     */
    private List<GroupRecentMessage> recentGroupMessages;

    /**
     * 本次发言顺序列表（群聊 chat 分支使用）。
     * 由 opc-im 决策层（GroupResponderPicker）产出，执行层（GroupBuilder）按此顺序串行逐个流式执行。
     */
    private List<String> responderAgentCodes;

    /**
     * MCP 编码列表（工作流模式时使用）
     */
    private List<String> mcpCodes;

    /**
     * 技能路径列表（工作流模式时使用）
     */
    private List<String> skillPaths;

    /**
     * 主工作空间（当前 Agent 的工作空间）。
     * <p>
     * 作为文件读写的默认边界，也是相对路径的解析基准。
     */
    private String primaryWorkspace;

    /**
     * 共享工作空间（群 / 专家团的公共产出目录）。
     * <p>
     * 只作为<b>额外可写目录</b>追加，不放宽成员自身边界，避免"进群即提权"。
     */
    private String sharedWorkspace;

    /** 扩展参数（透传给 Builder） */
    private Map<String, Object> params;

    /**
     * 任务ID（定时任务场景下使用）
     */
    private String taskCode;

    /**
     * 定时任务触发时的锚点上下文字符串。
     * <p>
     * 由 AgentTaskConsumer 在任务触发时根据 anchorMsgCode 查库拼装，
     * 注入到 system prompt 末尾，帮助 AI 还原"创建任务时的对话上下文"。
     * 非定时任务场景下为空。
     */
    private String taskContext;

    /**
     *
     */
    private String prompt;

    /**
     * 是否启用思考模式
     */
    private boolean enableThinking;

    /**
     * 图片生成规范
     */
    private ImageBuildSpec imageBuildSpec;


    @Data
    @Builder
    public static class ImageBuildSpec {

        /**
         * 图片生成类型：0-文本生成图片
         */
        public static int TYPE_TEXT2IMAGE = 0;

        /**
         * 图片生成类型：1-图生图
         */
        public static int TYPE_IMAGE2IMAGE = 1;

        private String size;

        private String style;

        /**
         * 图片地址列表（图生图模式）
         */
        private List<String> urls;

        /**
         * 图片数量
         */
        private int n = 1;


        private int mode = 0;

    }

    /**
     * 群聊上下文中的一条最近消息（含 sender），供决策层做「衔接判断」与「能力匹配」。
     */
    @Data
    @Builder
    public static class GroupRecentMessage {

        /** 发言者编码（用户或 Agent） */
        private String senderCode;

        /** 发言者类型：USER / AGENT */
        private String senderType;

        /** 消息内容 */
        private String content;

        /** 透传 ChatMessage.messageType */
        private String messageType;
    }
}



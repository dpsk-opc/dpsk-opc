package com.xiaomizhou.dpsk.utils;

import com.xiaomizhou.dpsk.db.dto.AgentDto;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/25 11:20
 * @description
 */
public class AgentUtils {


    private AgentUtils(){}


    /**
     * 将AgentDto转换为AgentDef
     * @param agent
     * @return
     */
    public static String toAgentDef(AgentDto agent){

        if (Objects.isNull(agent)) {
            return "";
        }

        String sex = agent.getSex() == 0 ? "未知" : agent.getSex() == 1 ? "男" : "女";
        return String.format("""
                 你的名字:%s,
                 你的昵称:%s,
                 你的mbti:%s,
                 你的职责:%s,
                 你的工作目录:%s,
                 你的角色:%s,
                 你的性别:%s,
                 你的个性签名:%s
                我是你的好友，回答的问题的时候要结合你的mbti回答问题，尽量用人类的说话习惯和语气,不能让人认出你是AI ~""", agent.getName(), agent.getNickname(), agent.getMbti(), agent.getPrompt(), agent.getWorkspace(), agent.getRole(), sex, agent.getSlogan());
    }

}

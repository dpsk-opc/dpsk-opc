package com.xiaomizhou.dpsk.tool.buildin;

import com.xiaomizhou.dpsk.tool.ToolMeta;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/15 9:43
 * @description
 */
@ToolMeta(value = "获取当前时间", level = "normal", category = "系统", tags = {"日期时间"})
public class DateTimeTools {

    @Tool(name = "now", description = "获取当前用户所在时区当前时间")
    @dev.langchain4j.agent.tool.Tool(name = "now", value = "获取当前用户所在时区当前时间")
    public String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);
    }


    @Tool(name = "zoneNow", description = "获取特定时区的当前时间")
    @dev.langchain4j.agent.tool.Tool(name = "zoneNow", value = "获取特定时区的当前时间")
    public String zoneNow(@ToolParam(description = "标准时区.e.g:Asia/Shanghai") String zoneId) {
        return LocalDateTime.now(ZoneId.of(zoneId)).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

}

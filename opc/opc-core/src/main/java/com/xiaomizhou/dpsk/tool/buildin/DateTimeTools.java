package com.xiaomizhou.dpsk.tool.buildin;

import com.xiaomizhou.dpsk.tool.ToolMeta;
import dev.langchain4j.agent.tool.P;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.WeekFields;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/15 9:43
 * @description 日期时间工具，提供当前时间及时区相关信息
 */
@ToolMeta(value = "获取当前时间", level = "normal", category = "meta", tags = {"日期时间"})
public class DateTimeTools {

    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ISO_DATE_TIME;

    @dev.langchain4j.agent.tool.Tool(name = "now", value = "获取当前用户所在时区当前时间")
    public String now() {
        return buildTimeInfo(ZonedDateTime.now());
    }

    @dev.langchain4j.agent.tool.Tool(name = "zoneNow", value = "获取特定时区的当前时间")
    public String zoneNow(@P(description = "标准时区.e.g:Asia/Shanghai") String zoneId) {
        ZonedDateTime zdt = ZonedDateTime.now(ZoneId.of(zoneId));
        return buildTimeInfo(zdt);
    }

    /**
     * 构建详细的时间信息，包括日期、时间、星期、时区等。
     */
    private String buildTimeInfo(ZonedDateTime zdt) {
        ZoneId zone = zdt.getZone();
        DayOfWeek dayOfWeek = zdt.getDayOfWeek();
        int dayOfYear = zdt.getDayOfYear();
        int weekOfYear = zdt.get(WeekFields.of(Locale.getDefault()).weekOfYear());
        boolean isDst = zone.getRules().isDaylightSavings(zdt.toInstant());

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("datetime", zdt.format(ISO_FMT));
        info.put("timezone", zone.getId());
        info.put("timezone_offset", zdt.getOffset().getId());
        info.put("day_of_week", dayOfWeek.getValue() + " (" + dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINESE) + ")");
        info.put("day_of_year", dayOfYear);
        info.put("week_of_year", weekOfYear);
        info.put("is_dst", isDst);

        // 简单 JSON 格式输出，避免引入 Jackson 依赖
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        int count = 0;
        for (Map.Entry<String, Object> entry : info.entrySet()) {
            if (count > 0) sb.append(",\n");
            sb.append("  \"").append(entry.getKey()).append("\": ");
            Object val = entry.getValue();
            if (val instanceof String || val instanceof Enum<?>) {
                sb.append("\"").append(val).append("\"");
            } else {
                sb.append(val);
            }
            count++;
        }
        sb.append("\n}");
        return sb.toString();
    }

}

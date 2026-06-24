package com.xiaomizhou.dpsk.tool;

import com.xiaomizhou.dpsk.db.TodoComponent;
import com.xiaomizhou.dpsk.db.dto.TodoCreateCmd;
import com.xiaomizhou.dpsk.db.dto.TodoItemDto;
import com.xiaomizhou.dpsk.db.dto.TodoUpdateCmd;
import com.xiaomizhou.dpsk.tool.model.ToolContext;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * AI 可调用的待办事项工具。
 * <p>
 * Agent 在对话中调用此工具即可管理待办：
 * <ul>
 *   <li>add_todo — 新增待办</li>
 *   <li>update_todo — 修改待办</li>
 *   <li>delete_todo — 删除待办</li>
 *   <li>list_todos — 查询待办列表</li>
 * </ul>
 * <p>
 * agentCode/conversationCode 通过 {@link ToolContext} 透传，
 * 不对外暴露给 LLM，避免大模型产生幻觉值。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/24
 */
@ToolMeta(value = "待办事项管理，支持新增、修改、删除、查询待办列表",
        level = "normal",
        category = ToolCategory.BUILD_IN,
        tags = {"TODO", "待办"})
@Slf4j
@RequiredArgsConstructor
@Component
public class TodoTools {

    private final TodoComponent todoComponent;

    /**
     * 新增待办事项。
     *
     * @param title      待办名称
     * @param content    待办内容
     * @param dueTime    逾期时间，格式 yyyy-MM-dd HH:mm:ss，如 "2026-06-25 14:30:00"
     * @param alarmEnabled 是否开启提醒
     * @param toolContext 工具上下文（透传，LLM 不可见）
     * @return 创建结果
     */
    @Tool(name = "add_todo", value = "新增待办事项")
    public String addTodo(
            @P(name = "title", description = "待办名称", required = true) String title,
            @P(name = "content", description = "待办内容", required = true) String content,
            @P(name = "dueTime", description = "逾期时间，格式 yyyy-MM-dd HH:mm:ss，如 2026-06-25 14:30:00", required = true) String dueTime,
            @P(name = "alarmEnabled", description = "是否开启提醒", required = true) boolean alarmEnabled,
            @P(name = "toolContext", description = "工具上下文，不能传这个参数", required = false) ToolContext toolContext) {

        String agentCode = toolContext.getAgentCode();
        String conversationCode = toolContext.getConversationCode();
        String ownerCode = toolContext.getUserCode();

        try {
            if (StringUtils.isAnyBlank(title, content, dueTime)) {
                return "新增待办失败：缺少必填参数（title, content, dueTime）";
            }
            if (StringUtils.isBlank(agentCode)) {
                return "新增待办失败：缺少透传参数 agentCode，请检查上下文是否已初始化";
            }

            TodoCreateCmd cmd = new TodoCreateCmd();
            cmd.setAgentId(agentCode);
            cmd.setTitle(title);
            cmd.setContent(content);
            cmd.setDueTime(parseDateTime(dueTime));
            cmd.setAlarmEnabled(alarmEnabled);
            cmd.setStatus(0); // pending
            cmd.setConversationCode(conversationCode);

            TodoItemDto dto = todoComponent.create(cmd, ownerCode);
            log.info("TodoTools 新增待办成功: code={}, title={}", dto.getId(), title);

            return String.format("已成功新增待办「%s」，待办编码: %s，逾期时间: %s",
                    title, dto.getId(), dueTime);

        } catch (Exception e) {
            log.error("TodoTools 新增待办失败", e);
            return "新增待办失败: " + e.getMessage();
        }
    }

    /**
     * 修改待办事项。
     *
     * @param id          待办编码
     * @param title       待办名称（可选）
     * @param content     待办内容（可选）
     * @param dueTime     逾期时间（可选）
     * @param alarmEnabled 是否开启提醒（可选）
     * @param status      状态（可选）：0=pending, 1=in_progress, 2=done
     * @param toolContext 工具上下文（透传，LLM 不可见）
     * @return 更新结果
     */
    @Tool(name = "update_todo", value = "修改待办事项")
    public String updateTodo(
            @P(name = "id", description = "待办编码", required = true) String id,
            @P(name = "title", description = "待办名称") String title,
            @P(name = "content", description = "待办内容") String content,
            @P(name = "dueTime", description = "逾期时间，格式 yyyy-MM-dd HH:mm:ss") String dueTime,
            @P(name = "alarmEnabled", description = "是否开启提醒") Boolean alarmEnabled,
            @P(name = "status", description = "状态：0=pending, 1=in_progress, 2=done") Integer status,
            @P(name = "toolContext", description = "工具上下文，不能传这个参数", required = false) ToolContext toolContext) {

        try {
            if (StringUtils.isBlank(id)) {
                return "修改待办失败：缺少必填参数 id";
            }

            TodoUpdateCmd cmd = new TodoUpdateCmd();
            cmd.setId(id);
            if (StringUtils.isNotBlank(title)) {
                cmd.setTitle(title);
            }
            if (StringUtils.isNotBlank(content)) {
                cmd.setContent(content);
            }
            if (StringUtils.isNotBlank(dueTime)) {
                cmd.setDueTime(parseDateTime(dueTime));
            }
            if (alarmEnabled != null) {
                cmd.setAlarmEnabled(alarmEnabled);
            }
            if (status != null) {
                cmd.setStatus(status);
            }

            TodoItemDto dto = todoComponent.update(cmd);
            log.info("TodoTools 修改待办成功: code={}", id);

            return String.format("已成功修改待办「%s」", dto.getTitle());

        } catch (Exception e) {
            log.error("TodoTools 修改待办失败", e);
            return "修改待办失败: " + e.getMessage();
        }
    }

    /**
     * 删除待办事项。
     *
     * @param id          待办编码
     * @param toolContext 工具上下文（透传，LLM 不可见）
     * @return 删除结果
     */
    @Tool(name = "delete_todo", value = "删除待办事项")
    public String deleteTodo(
            @P(name = "id", description = "待办编码", required = true) String id,
            @P(name = "toolContext", description = "工具上下文，不能传这个参数", required = false) ToolContext toolContext) {

        try {
            if (StringUtils.isBlank(id)) {
                return "删除待办失败：缺少必填参数 id";
            }

            todoComponent.delete(id);
            log.info("TodoTools 删除待办成功: code={}", id);

            return String.format("已成功删除待办，编码: %s", id);

        } catch (Exception e) {
            log.error("TodoTools 删除待办失败", e);
            return "删除待办失败: " + e.getMessage();
        }
    }

    /**
     * 查询待办列表。
     *
     * @param status      状态筛选（可选）：0=pending, 1=in_progress, 2=done，不传查全部
     * @param toolContext 工具上下文（透传，LLM 不可见）
     * @return 待办列表
     */
    @Tool(name = "list_todos", value = "查询待办列表")
    public String listTodos(
            @P(name = "status", description = "状态筛选：0=待办, 1=进行中, 2=已完成，不传查全部") Integer status,
            @P(name = "toolContext", description = "工具上下文，不能传这个参数", required = false) ToolContext toolContext) {

        String agentCode = toolContext.getAgentCode();

        try {
            if (StringUtils.isBlank(agentCode)) {
                return "查询待办列表失败：缺少透传参数 agentCode，请检查上下文是否已初始化";
            }

            var pair = todoComponent.pageByAgent(agentCode, status, 1, 50);
            List<TodoItemDto> list = pair.getRight();

            if (list.isEmpty()) {
                return "当前没有待办事项。";
            }

            String result = list.stream()
                    .map(dto -> String.format("- [%s] %s（%s，逾期: %s）",
                            statusLabel(dto.getStatus()),
                            dto.getTitle(),
                            dto.getId(),
                            dto.getDueTime() != null ? dto.getDueTime().toString() : "无"))
                    .collect(Collectors.joining("\n"));

            return String.format("共 %d 条待办事项：\n%s", pair.getLeft(), result);

        } catch (Exception e) {
            log.error("TodoTools 查询待办列表失败", e);
            return "查询待办列表失败: " + e.getMessage();
        }
    }

    // ======================== 工具方法 ========================

    /**
     * 解析日期字符串 → Date。
     */
    private static Date parseDateTime(String dateStr) {
        if (StringUtils.isBlank(dateStr)) return null;
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return sdf.parse(dateStr);
        } catch (Exception e) {
            log.warn("TodoTools 解析日期失败: {}", dateStr, e);
            return null;
        }
    }

    /**
     * 状态数字 → 中文标签。
     */
    private static String statusLabel(Integer status) {
        if (status == null) return "待办";
        return switch (status) {
            case 1 -> "进行中";
            case 2 -> "已完成";
            default -> "待办";
        };
    }
}

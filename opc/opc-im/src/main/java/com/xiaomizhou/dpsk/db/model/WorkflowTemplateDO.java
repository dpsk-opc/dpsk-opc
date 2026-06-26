package com.xiaomizhou.dpsk.db.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaomizhou.dpsk.db.model.base.BaseModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工作流模板实体类。
 * <p>
 * 分类使用 Integer 数字类型：
 * <ul>
 *   <li>0 — 通用</li>
 *   <li>1 — 编程</li>
 *   <li>2 — 运维</li>
 *   <li>3 — 办公</li>
 *   <li>4 — 其他</li>
 * </ul>
 * 状态使用 Integer 数字类型：
 * <ul>
 *   <li>0 — 草稿</li>
 *   <li>1 — 已发布</li>
 *   <li>2 — 已停用</li>
 * </ul>
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/25
 */
@EqualsAndHashCode(callSuper = true)
@TableName("t_workflow_template")
@Data
public class WorkflowTemplateDO extends BaseModel {

    /** 模板编码，唯一标识 */
    @TableField("code")
    private String code;

    /** 模板名称 */
    @TableField("name")
    private String name;

    /** 模板描述 */
    @TableField("description")
    private String description;

    /** 模板分类 */
    @TableField("category")
    private Integer category;

    /** 节点数量 */
    @TableField("node_count")
    private Integer nodeCount;

    /** 状态 */
    @TableField("status")
    private Integer status;

    /** 版本号 */
    @TableField("version")
    private Integer version;

    /** 工作流DAG JSON */
    @TableField("workflow_json")
    private String workflowJson;

    /** 输入参数JSON Schema */
    @TableField("input_schema")
    private String inputSchema;

    /** 超时时间（秒） */
    @TableField("timeout_seconds")
    private Integer timeoutSeconds;

    /** 失败策略 */
    @TableField("failure_strategy")
    private Integer failureStrategy;

    /** 头像 */
    @TableField("avatar")
    private String avatar;

    /** 最大重试次数 */
    @TableField("max_retry")
    private Integer maxRetry;

    /** 完成时通知 */
    @TableField("notify_on_complete")
    private Integer notifyOnComplete;

    /** 创建者编码 */
    @TableField("owner_code")
    private String ownerCode;

    // ======================== 分类常量 ========================

    public static final int CATEGORY_GENERAL = 0;
    public static final int CATEGORY_DEV = 1;
    public static final int CATEGORY_OPS = 2;
    public static final int CATEGORY_OFFICE = 3;
    public static final int CATEGORY_OTHER = 4;

    // ======================== 状态常量 ========================

    public static final int STATUS_DRAFT = 0;
    public static final int STATUS_PUBLISHED = 1;
    public static final int STATUS_DISABLED = 2;

    // ======================== 失败策略常量 ========================

    public static final int FAILURE_STOP = 0;
    public static final int FAILURE_SKIP = 1;
    public static final int FAILURE_RETRY = 2;

    /**
     * 获取分类中文名称。
     */
    public static String categoryName(Integer category) {
        if (category == null) return "未知";
        return switch (category) {
            case CATEGORY_GENERAL -> "通用";
            case CATEGORY_DEV -> "编程";
            case CATEGORY_OPS -> "运维";
            case CATEGORY_OFFICE -> "办公";
            case CATEGORY_OTHER -> "其他";
            default -> "未知";
        };
    }

    /**
     * 获取状态中文名称。
     */
    public static String statusName(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case STATUS_DRAFT -> "草稿";
            case STATUS_PUBLISHED -> "已发布";
            case STATUS_DISABLED -> "已停用";
            default -> "未知";
        };
    }
}

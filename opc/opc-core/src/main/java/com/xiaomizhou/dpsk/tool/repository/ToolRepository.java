package com.xiaomizhou.dpsk.tool.repository;

import com.xiaomizhou.dpsk.tool.model.ToolMetadata;

import java.util.List;

/**
 * 工具元数据仓储接口。
 * <p>
 * opc-core 仅定义接口，具体实现由 opc-im 基于 MyBatis-Plus 提供。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/30
 */
public interface ToolRepository {

    /**
     * 查询所有启用的工具。
     *
     * @return 工具元数据列表
     */
    List<ToolMetadata> findAllEnabled();

    /**
     * 根据工具名称查询。
     *
     * @param name 工具名称
     * @return 工具元数据，无则返回 null
     */
    ToolMetadata findByName(String name);

    /**
     * 根据工具编码查询。
     *
     * @param code 工具编码
     * @return 工具元数据，无则返回 null
     */
    ToolMetadata findByCode(String code);

    /**
     * 根据来源类型查询启用的工具。
     *
     * @param sourceType 来源类型（LOCAL/MCP/SCRIPT）
     * @return 工具元数据列表
     */
    List<ToolMetadata> findBySourceType(String sourceType);

    /**
     * 按照分类查询。
     *
     * @param category 工具分类
     * @return 工具元数据列表
     */
    List<ToolMetadata> findByCategory(String category);

    /**
     * 根据所属 Agent 查询启用的工具（包含公共工具）。
     *
     * @param ownerAgentCode Agent 编码，为空则查公共工具
     * @return 工具元数据列表
     */
    List<ToolMetadata> findByOwnerAgent(String ownerAgentCode);

    /**
     * 关键词搜索工具（匹配 name 和 description）。
     *
     * @param keyword 关键词
     * @return 匹配的工具元数据列表
     */
    List<ToolMetadata> searchByKeyword(String keyword);

    /**
     * 保存/更新工具元数据。
     *
     * @param metadata 工具元数据
     */
    void save(ToolMetadata metadata);

    /**
     * 批量保存工具元数据。
     *
     * @param metadataList 工具元数据列表
     */
    void saveBatch(List<ToolMetadata> metadataList);

    /**
     * 根据编码更新状态。
     *
     * @param code   工具编码
     * @param status 状态（ENABLED/DISABLED）
     */
    void updateStatus(String code, String status);

    /**
     * 逻辑删除工具。
     *
     * @param code 工具编码
     */
    void deleteByCode(String code);

    /**
     * 检查工具编码是否存在。
     *
     * @param code 工具编码
     * @return 存在返回 true
     */
    boolean existsByCode(String code);
}

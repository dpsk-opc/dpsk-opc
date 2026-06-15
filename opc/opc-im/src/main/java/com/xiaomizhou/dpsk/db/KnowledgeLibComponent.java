package com.xiaomizhou.dpsk.db;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.xiaomizhou.dpsk.constant.FileRefType;
import com.xiaomizhou.dpsk.constant.KnowledgeLibStatus;
import com.xiaomizhou.dpsk.constant.KnowledgeNodeType;
import com.xiaomizhou.dpsk.constant.OwnerType;
import com.xiaomizhou.dpsk.core.exceptions.BusinessException;
import com.xiaomizhou.dpsk.db.dao.FileRecordDao;
import com.xiaomizhou.dpsk.db.dao.KnowledgeLibDao;
import com.xiaomizhou.dpsk.db.dao.KnowledgeNodeDao;
import com.xiaomizhou.dpsk.db.dto.KnowledgeNodeDto;
import com.xiaomizhou.dpsk.db.model.FileRecord;
import com.xiaomizhou.dpsk.db.model.KnowledgeLib;
import com.xiaomizhou.dpsk.db.model.KnowledgeNode;
import com.xiaomizhou.dpsk.utils.SequenceUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 知识库业务组件
 * 管理知识库元信息 + 目录树节点 + 文件关联
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/13
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class KnowledgeLibComponent {

    private final KnowledgeLibDao knowledgeLibDao;
    private final KnowledgeNodeDao knowledgeNodeDao;
    private final FileRecordDao fileRecordDao;

    private final FileService fileService;

    // ==================== 知识库 CRUD ====================

    /**
     * 初始化知识库（Agent 创建时调用）
     *
     * @param agentCode Agent 编码
     * @param agentName Agent 名称
     * @return 初始化的知识库
     */
    @Transactional(rollbackFor = Exception.class)
    public String init(String agentCode, String agentName) {
        KnowledgeLib lib = new KnowledgeLib();
        lib.setCode(SequenceUtils.generator().next("KL"));
        lib.setName(agentName + "的知识库");
        lib.setDescription("");
        lib.setOwnerCode(agentCode);
        lib.setOwnerType(OwnerType.AGENT);
        lib.setStatus(KnowledgeLibStatus.UPLOADED);
        lib.setCreateTime(new Date());
        lib.setUpdateTime(new Date());

        knowledgeLibDao.save(lib);
        log.info("知识库初始化成功: code={}, ownerCode={}", lib.getCode(), agentCode);
        return lib.getCode();
    }

    /**
     * 根据编码查询知识库
     */
    public KnowledgeLib getByCode(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        return knowledgeLibDao.getByCode(code);
    }

    /**
     * 根据归属实体查询知识库（一个 Agent 只有一个）
     */
    public KnowledgeLib getByOwner(String ownerCode, Integer ownerType) {
        List<KnowledgeLib> libs = listByOwnerCodes(Lists.newArrayList(ownerCode), ownerType);
        return libs.isEmpty() ? null : libs.get(0);
    }

    /**
     * 根据归属实体查询知识库列表  一个agent只有一个知识库
     *
     * @param ownerCodes
     * @param ownerType
     * @return
     */
    public List<KnowledgeLib> listByOwnerCodes(List<String> ownerCodes, Integer ownerType) {

        if (CollectionUtils.isEmpty(ownerCodes) || Objects.isNull(ownerType)) {
            return Lists.newArrayList();
        }

        return knowledgeLibDao.list(Wrappers.<KnowledgeLib>lambdaQuery().in(KnowledgeLib::getOwnerCode, ownerCodes).eq(KnowledgeLib::getOwnerType, ownerType));
    }


    /**
     * 更新知识库名称和描述
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean update(String code, String name, String description) {
        KnowledgeLib lib = getByCode(code);
        if (lib == null) {
            return false;
        }
        if (StringUtils.isNotBlank(name)) {
            lib.setName(name);
        }
        if (description != null) {
            lib.setDescription(description);
        }
        lib.setUpdateTime(new Date());
        return knowledgeLibDao.updateById(lib);
    }

    /**
     * 删除知识库（逻辑删除，同时删除所有节点）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteByCode(String code) {
        KnowledgeLib lib = getByCode(code);
        if (lib == null) {
            return false;
        }
        // 删除所有节点
        List<KnowledgeNode> allNodes = knowledgeNodeDao.listByLibCode(code);
        for (KnowledgeNode node : allNodes) {
            knowledgeNodeDao.removeById(node);
        }
        knowledgeLibDao.removeById(lib);
        log.info("知识库删除成功: code={}, 删除节点数={}", code, allNodes.size());
        return true;
    }

    // ==================== 节点管理 ====================

    /**
     * 查询指定父节点下的子节点列表（懒加载）
     *
     * @param parentCode 父节点编码，空字符串表示查根节点
     * @return 子节点列表（按 sort_order 排序）
     */
    public List<KnowledgeNodeDto> listChildNodes(String parentCode,String libCode) {

        if(StringUtils.isBlank(libCode)){
            return Collections.emptyList();
        }

        List<KnowledgeNode> nodes = knowledgeNodeDao.listByParentCode(StringUtils.defaultString(parentCode, ""),libCode);

        if (CollectionUtils.isEmpty(nodes)) {
            return Collections.emptyList();
        }

        List<String> fileCodes = nodes.stream().map(KnowledgeNode::getFileCode).collect(Collectors.toList());
        Map<String, FileRecord> files = fileRecordDao.list(Wrappers.<FileRecord>lambdaQuery().in(FileRecord::getCode, fileCodes)).stream().collect(Collectors.toMap(FileRecord::getCode, Function.identity(), (k1, k2) -> k1));


        List<String> nodeCodes = nodes.stream().map(KnowledgeNode::getCode).collect(Collectors.toList());
        Map<String, Integer> nodeCountMap = getNodeCountByLibCode(nodeCodes);

        return nodes.stream().map(node -> {
            KnowledgeNodeDto dto = KnowledgeNodeDto.toKnowledgeNodeDto(node);

            String fileCode = node.getFileCode();
            if (StringUtils.isNotBlank(fileCode) && files.containsKey(fileCode)) {
                FileRecord file = files.get(fileCode);
                dto.setAccessUrl(fileService.getDownloadUrl(file));
                dto.setFileSize(file.getFileSize());
                dto.setContentType(file.getContentType());
                dto.setHasChildren(Objects.nonNull(nodeCountMap) && nodeCountMap.containsKey(node.getCode()));
            }

            return dto;
        }).collect(Collectors.toList());
    }

    /**
     *
     * @param nodeCodes
     * @return
     */
    public Map<String,Integer> getNodeCountByLibCode(List<String> nodeCodes) {

        if(CollectionUtils.isEmpty(nodeCodes)){
            return Map.of();
        }

        // select count(*) from knowledge_node where parent_code in (?) group by node_code
        List<Map<String, Integer>> maps = knowledgeNodeDao.getBaseMapper().getNodeCountByLibCode(nodeCodes);

        Map<String,Integer> result = Maps.newHashMap();
        for (Map<String, Integer> map : maps) {
            result.putAll(map);
        }
        return result;
    }

    /**
     * 创建目录节点
     *
     * @param libCode    知识库编码
     * @param parentCode 父节点编码（空字符串表示根目录）
     * @param name       目录名称
     * @return 创建的节点
     */
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeNode createFolder(String libCode, String parentCode, String name) {
        // 校验父节点（如果指定了父节点）
        int level = 0;
        if (StringUtils.isNotBlank(parentCode)) {
            KnowledgeNode parent = knowledgeNodeDao.getByCode(parentCode);
            if (parent == null) {
                throw BusinessException.notFound("父节点不存在: " + parentCode);
            }
            if (parent.getNodeType() == KnowledgeNodeType.FILE) {
                throw BusinessException.businessError("文件节点下不能创建子节点");
            }
            level = parent.getLevel() + 1;
        }

        KnowledgeNode node = new KnowledgeNode();
        node.setCode(SequenceUtils.generator().next("KN"));
        node.setLibCode(libCode);
        node.setParentCode(StringUtils.defaultString(parentCode, ""));
        node.setName(name);
        node.setNodeType(KnowledgeNodeType.FOLDER);
        node.setLevel(level);
        node.setFileCode("");
        node.setSortOrder(0);
        node.setCreateTime(new Date());
        node.setUpdateTime(new Date());

        knowledgeNodeDao.save(node);
        log.info("目录节点创建成功: code={}, name={}, parentCode={}", node.getCode(), name, parentCode);
        return node;
    }

    /**
     * 保存文件节点（将已上传的文件关联到知识库目录下）
     *
     * @param libCode    知识库编码
     * @param parentCode 父节点编码（空字符串表示根目录）
     * @param fileCode   已上传的文件编码（t_file_record.code）
     * @param name       文件在知识库中的显示名称
     * @return 创建的节点
     */
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeNode saveFileNode(String libCode, String parentCode, String fileCode, String name) {
        // 校验文件是否存在
        FileRecord file = fileRecordDao.getByCode(fileCode);
        if (file == null) {
            throw BusinessException.notFound("文件不存在: " + fileCode);
        }

        // 校验父节点
        int level = 0;
        if (StringUtils.isNotBlank(parentCode)) {
            KnowledgeNode parent = knowledgeNodeDao.getByCode(parentCode);
            if (parent == null) {
                throw BusinessException.notFound("父节点不存在: " + parentCode);
            }
            if (parent.getNodeType() == KnowledgeNodeType.FILE) {
                throw BusinessException.businessError("文件节点下不能创建子节点");
            }
            level = parent.getLevel() + 1;
        }

        KnowledgeNode node = new KnowledgeNode();
        node.setCode(SequenceUtils.generator().next("KN"));
        node.setLibCode(libCode);
        node.setParentCode(StringUtils.defaultString(parentCode, ""));
        node.setName(StringUtils.defaultIfBlank(name, file.getOriginalName()));
        node.setNodeType(KnowledgeNodeType.FILE);
        node.setLevel(level);
        node.setFileCode(fileCode);
        node.setSortOrder(0);
        node.setCreateTime(new Date());
        node.setUpdateTime(new Date());

        knowledgeNodeDao.save(node);

        // 刷新文件的关联信息
        fileRecordDao.update(null,
                Wrappers.<FileRecord>lambdaUpdate()
                        .set(FileRecord::getRefCode, node.getCode())
                        .set(FileRecord::getRefType, FileRefType.KNOWLEDGE_NODE)
                        .eq(FileRecord::getCode, fileCode));

        log.info("文件节点保存成功: nodeCode={}, fileCode={}, parentCode={}", node.getCode(), fileCode, parentCode);
        return node;
    }

    /**
     * 更新节点（重命名）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean updateNode(String nodeCode, String name) {
        KnowledgeNode node = knowledgeNodeDao.getByCode(nodeCode);
        if (node == null) {
            return false;
        }
        if (StringUtils.isNotBlank(name)) {
            node.setName(name);
        }
        node.setUpdateTime(new Date());
        return knowledgeNodeDao.updateById(node);
    }

    /**
     * 删除节点
     * - 目录节点：级联删除所有子孙节点
     * - 文件节点：仅删除节点本身，不删除 t_file_record
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteNode(String nodeCode) {
        KnowledgeNode node = knowledgeNodeDao.getByCode(nodeCode);
        if (node == null) {
            return false;
        }

        if (node.getNodeType() == KnowledgeNodeType.FOLDER) {
            // 递归删除所有子孙节点
            List<KnowledgeNode> descendants = knowledgeNodeDao.listDescendants(nodeCode, node.getLibCode());
            for (KnowledgeNode child : descendants) {
                knowledgeNodeDao.removeById(child);
            }
            log.info("目录节点删除成功: code={}, 级联删除子节点数={}", nodeCode, descendants.size());
        }

        knowledgeNodeDao.removeById(node);
        log.info("节点删除成功: code={}", nodeCode);
        return true;
    }

    /**
     * 移动节点到新的父节点下
     *
     * @param nodeCode       要移动的节点编码
     * @param newParentCode  新的父节点编码（空字符串表示根目录）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean moveNode(String nodeCode, String newParentCode) {
        KnowledgeNode node = knowledgeNodeDao.getByCode(nodeCode);
        if (node == null) {
            return false;
        }

        // 校验不能移动到自己的子节点下
        if (StringUtils.isNotBlank(newParentCode)) {
            KnowledgeNode newParent = knowledgeNodeDao.getByCode(newParentCode);
            if (newParent == null) {
                throw BusinessException.notFound("目标父节点不存在: " + newParentCode);
            }
            if (newParent.getNodeType() == KnowledgeNodeType.FILE) {
                throw BusinessException.businessError("不能移动到文件节点下");
            }
            // 重新计算 level
            int newLevel = StringUtils.isBlank(newParentCode) ? 0 : newParent.getLevel() + 1;
            int levelDiff = newLevel - node.getLevel();

            node.setParentCode(StringUtils.defaultString(newParentCode, ""));
            node.setLevel(newLevel);
            node.setUpdateTime(new Date());
            knowledgeNodeDao.updateById(node);

            // 如果是目录节点，递归更新所有子孙的 level
            if (node.getNodeType() == KnowledgeNodeType.FOLDER && levelDiff != 0) {
                List<KnowledgeNode> descendants = knowledgeNodeDao.listDescendants(nodeCode, node.getLibCode());
                for (KnowledgeNode child : descendants) {
                    child.setLevel(child.getLevel() + levelDiff);
                    child.setUpdateTime(new Date());
                    knowledgeNodeDao.updateById(child);
                }
            }
        } else {
            int levelDiff = -node.getLevel();
            node.setParentCode("");
            node.setLevel(0);
            node.setUpdateTime(new Date());
            knowledgeNodeDao.updateById(node);

            if (node.getNodeType() == KnowledgeNodeType.FOLDER && levelDiff != 0) {
                List<KnowledgeNode> descendants = knowledgeNodeDao.listDescendants(nodeCode, node.getLibCode());
                for (KnowledgeNode child : descendants) {
                    child.setLevel(child.getLevel() + levelDiff);
                    child.setUpdateTime(new Date());
                    knowledgeNodeDao.updateById(child);
                }
            }
        }

        log.info("节点移动成功: nodeCode={}, newParentCode={}", nodeCode, newParentCode);
        return true;
    }

}

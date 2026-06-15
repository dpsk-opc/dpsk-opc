package com.xiaomizhou.dpsk.controller;

import com.xiaomizhou.dpsk.constant.OwnerType;
import com.xiaomizhou.dpsk.core.exceptions.BusinessException;
import com.xiaomizhou.dpsk.core.exceptions.OpErrorCode;
import com.xiaomizhou.dpsk.core.model.Results;
import com.xiaomizhou.dpsk.core.model.request.Request;
import com.xiaomizhou.dpsk.core.model.response.Response;
import com.xiaomizhou.dpsk.db.KnowledgeLibComponent;
import com.xiaomizhou.dpsk.db.dto.*;
import com.xiaomizhou.dpsk.db.model.KnowledgeLib;
import com.xiaomizhou.dpsk.db.model.KnowledgeNode;
import com.xiaomizhou.dpsk.utils.AuthContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import retrofit2.http.POST;

import java.util.List;
import java.util.Objects;

/**
 * 知识库控制器
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/13
 */
@RestController
@RequestMapping(value = "xiaomizhou/opc/v1/knowledge-lib")
@Slf4j
@RequiredArgsConstructor
public class KnowledgeLibController {

    private final KnowledgeLibComponent knowledgeLibComponent;

    // ==================== 知识库 ====================

    /**
     * 查询当前 Agent 的知识库信息
     */
    @GetMapping("my")
    public Response<KnowledgeLibDto> my() {
        String agentCode = AuthContext.getAgentCode();
        KnowledgeLib lib = knowledgeLibComponent.getByOwner(agentCode, OwnerType.AGENT);
        if (lib == null) {
            throw BusinessException.paramError("知识库不存在");
        }
        return Results.ok(KnowledgeLibDto.toKnowledgeLibDto(lib));
    }

    /**
     * 更新知识库名称和描述
     */
    @PostMapping("update")
    public Response<String> update(@RequestBody Request<KnowledgeLibUpdateCmd> request) {
        KnowledgeLibUpdateCmd cmd = request.getParam();
        if (cmd == null || cmd.getCode() == null || cmd.getCode().isBlank()) {
            return Results.fail(OpErrorCode.PARAM_ERROR.getCode(), "知识库编码不能为空");
        }
        boolean success = knowledgeLibComponent.update(cmd.getCode(), cmd.getName(), cmd.getDescription());
        if (!success) {
            return Results.fail(OpErrorCode.PARAM_ERROR.getCode(), "知识库不存在");
        }
        return Results.ok("更新成功");
    }

    // ==================== 节点管理 ====================

    /**
     * 查询子节点列表（懒加载）
     * 传 parentCode 查该目录下的子节点，不传则查根节点
     */
    @PostMapping(value = "nodes")
    public Response<List<KnowledgeNodeDto>> nodes(@RequestBody Request<KnowledgeNodeCreateFolderCmd> request) {
        KnowledgeNodeCreateFolderCmd param = request.getParam();
        String parentCode = Objects.isNull(param) ? "" : param.getParentCode();
        List<KnowledgeNodeDto> nodes = knowledgeLibComponent.listChildNodes(parentCode, param.getLibCode());
        return Results.ok(nodes);
    }

    /**
     * 创建目录节点
     */
    @PostMapping("nodes/create-folder")
    public Response<KnowledgeNodeDto> createFolder(@RequestBody Request<KnowledgeNodeCreateFolderCmd> request) {
        KnowledgeNodeCreateFolderCmd cmd = request.getParam();
        if (cmd == null) {
            return Results.fail(400, "参数不能为空");
        }
        if (cmd.getName() == null || cmd.getName().isBlank()) {
            return Results.fail(400, "目录名称不能为空");
        }
        if (cmd.getLibCode() == null || cmd.getLibCode().isBlank()) {
            return Results.fail(400, "知识库编码不能为空");
        }

        KnowledgeNode node = knowledgeLibComponent.createFolder(cmd.getLibCode(), cmd.getParentCode(), cmd.getName());
        return Results.ok(KnowledgeNodeDto.toKnowledgeNodeDto(node));
    }

    /**
     * 保存文件节点（关联已上传的文件到知识库）
     */
    @PostMapping("nodes/save-file")
    public Response<KnowledgeNodeDto> saveFile(@RequestBody Request<KnowledgeNodeSaveFileCmd> request) {
        KnowledgeNodeSaveFileCmd cmd = request.getParam();
        if (cmd == null) {
            return Results.fail(400, "参数不能为空");
        }
        if (cmd.getLibCode() == null || cmd.getLibCode().isBlank()) {
            return Results.fail(400, "知识库编码不能为空");
        }
        if (cmd.getFileCode() == null || cmd.getFileCode().isBlank()) {
            return Results.fail(400, "文件编码不能为空");
        }

        KnowledgeNode node = knowledgeLibComponent.saveFileNode(
                cmd.getLibCode(), cmd.getParentCode(), cmd.getFileCode(), cmd.getName());
        return Results.ok(KnowledgeNodeDto.toKnowledgeNodeDto(node));
    }

    /**
     * 更新节点（重命名）
     */
    @PostMapping("nodes/update")
    public Response<String> updateNode(@RequestBody Request<KnowledgeNodeUpdateCmd> request) {
        KnowledgeNodeUpdateCmd cmd = request.getParam();
        if (cmd == null) {
            return Results.fail(400, "参数不能为空");
        }
        if (cmd.getCode() == null || cmd.getCode().isBlank()) {
            return Results.fail(400, "节点编码不能为空");
        }
        if (cmd.getName() == null || cmd.getName().isBlank()) {
            return Results.fail(400, "节点名称不能为空");
        }

        boolean success = knowledgeLibComponent.updateNode(cmd.getCode(), cmd.getName());
        if (!success) {
            return Results.fail(404, "节点不存在");
        }
        return Results.ok("更新成功");
    }

    /**
     * 删除节点（目录级联删除）
     */
    @PostMapping("nodes/delete")
    public Response<String> deleteNode(@RequestBody Request<KnowledgeNodeDeleteCmd> request) {
        KnowledgeNodeDeleteCmd cmd = request.getParam();
        if (cmd == null || cmd.getCode() == null || cmd.getCode().isBlank()) {
            return Results.fail(400, "节点编码不能为空");
        }

        boolean success = knowledgeLibComponent.deleteNode(cmd.getCode());
        if (!success) {
            return Results.fail(404, "节点不存在");
        }
        return Results.ok("删除成功");
    }

    /**
     * 移动节点
     */
    @PostMapping("nodes/move")
    public Response<String> moveNode(@RequestBody Request<KnowledgeNodeMoveCmd> request) {
        KnowledgeNodeMoveCmd cmd = request.getParam();
        if (cmd == null || cmd.getCode() == null || cmd.getCode().isBlank()) {
            return Results.fail(400, "节点编码不能为空");
        }

        boolean success = knowledgeLibComponent.moveNode(cmd.getCode(), cmd.getParentCode());
        if (!success) {
            return Results.fail(404, "节点不存在");
        }
        return Results.ok("移动成功");
    }

}

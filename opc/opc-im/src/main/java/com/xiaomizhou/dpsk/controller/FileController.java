package com.xiaomizhou.dpsk.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaomizhou.dpsk.core.model.Results;
import com.xiaomizhou.dpsk.core.model.response.Response;
import com.xiaomizhou.dpsk.db.FileService;
import com.xiaomizhou.dpsk.db.dto.FileRecordDto;
import com.xiaomizhou.dpsk.db.model.FileRecord;
import com.xiaomizhou.dpsk.utils.AuthContext;
import com.xiaomizhou.dpsk.utils.SequenceUtils;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/21 9:09
 * @description 文件上传下载控制器
 */
@RestController
@RequestMapping(value = "xiaomizhou/opc/v1/file")
@Slf4j
@RequiredArgsConstructor
public class FileController {

    @Value("${spring.web.resources.static-locations}")
    private String uploadPath;

    @Value("${com.xiaomizhou.dpsk.file.host:http://0.0.0.0:8080/}")
    private String host;

    private final FileService fileService;

    private File uploadDir;

    @PostConstruct
    public void init() {
        uploadDir = new File(uploadPath);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }
    }

    /**
     * 文件上传接口
     *
     * @param file 上传的文件
     * @return 包含下载链接的响应
     */
    @PostMapping(value = "upload")
    public Response<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Results.fail(400, "上传文件不能为空");
        }

        FileRecord record = fileService.save(file, AuthContext.getAgentCode(), "OTHER", "");
        if (record == null) {
            return Results.fail(500, "文件上传失败");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("code", record.getCode());
        result.put("originalName", record.getOriginalName());
        result.put("storedName", record.getStoredName());
        result.put("fileSize", record.getFileSize());
        result.put("contentType", record.getContentType());
        result.put("accessUrl", fileService.getFileUrlByCode(record.getCode()));
        result.put("downloadUrl", host + "uploads/" + record.getFilePath());

        log.info("文件上传成功: code={}, originalName={}, size={} bytes", record.getCode(), record.getOriginalName(), record.getFileSize());
        return Results.ok(result);
    }


    /**
     * 文件下载/访问接口
     *
     * @param path     文件路径（相对路径：年/月/日/文件名）
     * @param request  HTTP请求
     * @param response HTTP响应
     * @return 文件资源
     */
    @GetMapping(value = "download/{path:.*}")
    public ResponseEntity<Resource> download(@PathVariable String path, HttpServletRequest request, HttpServletResponse response) {
        try {
            // 安全检查：防止路径遍历攻击
            if (path.contains("..") || path.contains("\\")) {
                return ResponseEntity.badRequest().build();
            }

            File file = new File(uploadDir, path);

            if (!file.exists() || !file.isFile()) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(file);

            // 获取文件类型
            String contentType = Files.probeContentType(file.toPath());
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getName() + "\"")
                    .body(resource);
        } catch (IOException e) {
            log.error("文件访问失败: {}", path, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 文件保存接口（带数据库记录）
     * 将文件写入磁盘，并在数据库中写入一条文件记录
     *
     * @param file         上传的文件
     * @return 文件记录信息（含 code、访问链接等）
     */
    @PostMapping(value = "save")
    public Response<Map<String, Object>> save(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Results.fail(400, "上传文件不能为空");
        }

        FileRecord record = fileService.save(file, AuthContext.getAgentCode(), "", "");
        if (record == null) {
            return Results.fail(500, "文件保存失败");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("code", record.getCode());
        result.put("originalName", record.getOriginalName());
        result.put("storedName", record.getStoredName());
        result.put("fileSize", record.getFileSize());
        result.put("contentType", record.getContentType());
        result.put("accessUrl", fileService.getFileUrlByCode(record.getCode()));
        result.put("downloadUrl", host + "uploads/" + record.getFilePath());

        return Results.ok(result);
    }

    /**
     * 根据文件编码获取文件信息
     * 传入 code，返回文件的访问链接和元数据
     *
     * @param code 文件编码
     * @return 文件信息（含访问链接）
     */
    @GetMapping(value = "info/{code}")
    public Response<Map<String, Object>> info(@PathVariable String code) {
        FileRecord record = fileService.getByCode(code);
        if (record == null) {
            return Results.fail(404, "文件不存在");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("code", record.getCode());
        result.put("originalName", record.getOriginalName());
        result.put("storedName", record.getStoredName());
        result.put("fileSize", record.getFileSize());
        result.put("contentType", record.getContentType());
        result.put("fileExtension", record.getFileExtension());
        result.put("accessUrl", fileService.getFileUrlByCode(code));
        result.put("downloadUrl", host + "uploads/" + record.getFilePath());
        result.put("createTime", record.getCreateTime());

        return Results.ok(result);
    }

    /**
     * 根据文件编码直接访问/下载文件
     * 传入 code，后端返回文件流
     *
     * @param code     文件编码
     * @param request  HTTP请求
     * @param response HTTP响应
     * @return 文件资源
     */
    @GetMapping(value = "access/{code}")
    public ResponseEntity<Resource> access(@PathVariable String code,
                                           HttpServletRequest request,
                                           HttpServletResponse response) {
        FileRecord record = fileService.getByCode(code);
        if (record == null) {
            return ResponseEntity.notFound().build();
        }

        File file = fileService.getDiskFileByCode(code);
        if (file == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            Resource resource = new FileSystemResource(file);
            String contentType = record.getContentType();
            if (StringUtils.isEmpty(contentType)) {
                contentType = Files.probeContentType(file.toPath());
            }
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + record.getOriginalName() + "\"")
                    .body(resource);
        } catch (IOException e) {
            log.error("文件访问失败: code={}", code, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 根据会话编码分页查询该会话下所有分享过的文件
     * JOIN 逻辑: t_file_record.ref_type = 1 AND t_file_record.ref_code = t_chat_message.code
     *
     * @param conversationCode 会话编码
     * @param pageNo           页码（默认1）
     * @param pageSize         每页大小（默认10）
     * @return 分页的文件记录
     */
    @GetMapping(value = "list-by-conversation")
    public Response<?> listByConversation(@RequestParam("conversationCode") String conversationCode,
                                          @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                          @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        if (conversationCode == null || conversationCode.isBlank()) {
            return Results.fail(400, "会话编码不能为空");
        }

        Page<FileRecordDto> page = fileService.pageFilesByConversationCode(conversationCode, pageNo, pageSize);
        return Results.page(page.getRecords(), pageNo, pageSize, page.getTotal());
    }
}

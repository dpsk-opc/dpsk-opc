package com.xiaomizhou.dpsk.controller;

import com.xiaomizhou.dpsk.utils.SequenceUtils;
import com.xiaomizhou.dpsk.core.model.Results;
import com.xiaomizhou.dpsk.core.model.response.Response;
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

    @Value("${com.xiaomizhou.dpsk.file.host:http://127.0.0.1:8080/}")
    private String host;

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
    public Response<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Results.fail(400, "上传文件不能为空");
        }

        try {
            // 获取原始文件名
            String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());

            // 检查文件名是否合法
            if (originalFilename.contains("..")) {
                return Results.fail(400, "文件名不合法");
            }

            // 生成唯一文件名：时间戳_UUID_原文件名
            String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
            String uuid = SequenceUtils.generator().next();
            String extension = getFileExtension(originalFilename);
            String newFilename = timestamp + "_" + uuid + extension;

            // 按日期创建子目录
            String datePath = new SimpleDateFormat("yyyy/MM/dd").format(new Date());
            File dateDir = new File(uploadDir, datePath);
            if (!dateDir.exists()) {
                dateDir.mkdirs();
            }
            // 保存文件
            File targetFile = new File(dateDir, newFilename);
            file.transferTo(targetFile);

            // 构建下载链接（相对于静态资源路径 /uploads/）
            String downloadUrl = host + "uploads/" + datePath + "/" + newFilename;

            // 返回结果
            Map<String, String> result = new HashMap<>();
            result.put("fileName", newFilename);
            result.put("originalName", originalFilename);
            result.put("downloadUrl", downloadUrl);
            result.put("fileSize", String.valueOf(file.getSize()));
            result.put("contentType", file.getContentType());

            log.info("文件上传成功: {}, 大小: {} bytes", originalFilename, file.getSize());

            return Results.ok(result);
        } catch (IOException e) {
            log.error("文件上传失败", e);
            return Results.fail(500, "文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 批量文件上传接口
     *
     * @param files 上传的文件数组
     * @return 包含下载链接的响应
     */
    @PostMapping(value = "upload/batch")
    public Response<Map<String, Object>> uploadBatch(@RequestParam("files") MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return Results.fail(400, "上传文件不能为空");
        }

        Map<String, Object> result = new HashMap<>();
        Map<String, String> successFiles = new HashMap<>();
        Map<String, String> failedFiles = new HashMap<>();

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }

            String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());

            try {
                if (originalFilename.contains("..")) {
                    failedFiles.put(originalFilename, "文件名不合法");
                    continue;
                }

                String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
                String uuid = SequenceUtils.generator().next();
                String extension = getFileExtension(originalFilename);
                String newFilename = timestamp + "_" + uuid + extension;

                String datePath = new SimpleDateFormat("yyyy/MM/dd").format(new Date());
                File dateDir = new File(uploadDir, datePath);
                if (!dateDir.exists()) {
                    dateDir.mkdirs();
                }

                Path targetPath = Paths.get(dateDir.getAbsolutePath(), newFilename);
                Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

                String downloadUrl = host + "uploads/" + datePath + "/" + newFilename;
                successFiles.put(originalFilename, downloadUrl);

                log.info("文件上传成功: {}, 大小: {} bytes", originalFilename, file.getSize());
            } catch (IOException e) {
                log.error("文件上传失败: {}", originalFilename, e);
                failedFiles.put(originalFilename, e.getMessage());
            }
        }

        result.put("successFiles", successFiles);
        result.put("failedFiles", failedFiles);
        result.put("successCount", successFiles.size());
        result.put("failedCount", failedFiles.size());

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
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0) {
            return "";
        }
        return filename.substring(dotIndex);
    }
}

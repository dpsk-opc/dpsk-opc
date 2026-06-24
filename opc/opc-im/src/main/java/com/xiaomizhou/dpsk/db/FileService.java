package com.xiaomizhou.dpsk.db;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.xiaomizhou.dpsk.constant.FileRefType;
import com.xiaomizhou.dpsk.db.dao.FileRecordDao;
import com.xiaomizhou.dpsk.db.dto.FileRecordDto;
import com.xiaomizhou.dpsk.db.model.FileRecord;
import com.xiaomizhou.dpsk.utils.SequenceUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 文件服务组件
 * 提供文件保存和读取的抽象方法，供后端各模块使用
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/12
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FileService {

    private final FileRecordDao fileRecordDao;

    @Value("${com.xiaomizhou.dpsk.file.upload.path}")
    private String uploadPath;

    @Value("${com.xiaomizhou.dpsk.file.host:http://0.0.0.0:8080/}")
    private String host;

    /**
     * 保存文件到磁盘并写入数据库记录
     *
     * @param file         上传的文件
     * @param uploaderCode 上传者编码
     * @param sourceType   文件来源类型：CHAT, TASK, AVATAR, OTHER
     * @param refCode      关联业务编码（可选）
     * @return 文件记录（包含 code），失败返回 null
     */
    public FileRecord save(MultipartFile file, String uploaderCode, String sourceType, String refCode) {
        if (file == null || file.isEmpty()) {
            log.warn("保存文件失败: 文件为空");
            return null;
        }

        try {
            String originalFilename = org.springframework.util.StringUtils.cleanPath(file.getOriginalFilename());

            if (originalFilename.contains("..")) {
                log.warn("保存文件失败: 文件名不合法 - {}", originalFilename);
                return null;
            }

            // 生成唯一 code
            String code = SequenceUtils.generator().next("FILE");

            // 生成存储文件名
            String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
            String uuid = SequenceUtils.generator().next();
            String extension = getFileExtension(originalFilename);
            String storedName = timestamp + "_" + uuid + extension;

            // 按日期创建子目录
            String datePath = new SimpleDateFormat("yyyy/MM/dd").format(new Date());
            File dateDir = new File(uploadPath, datePath);
            if (!dateDir.exists()) {
                dateDir.mkdirs();
            }

            // 写入磁盘
            File targetFile = new File(dateDir, storedName);
            file.transferTo(targetFile);

            // 构建数据库记录
            FileRecord record = new FileRecord();
            record.setCode(code);
            record.setOriginalName(originalFilename);
            record.setStoredName(storedName);
            record.setFilePath(datePath + "/" + storedName);
            record.setFileExtension(extension);
            record.setFileSize(file.getSize());
            record.setContentType(file.getContentType());
            record.setUploaderCode(uploaderCode);
            record.setRefCode(StringUtils.defaultString(refCode, ""));
            record.setSourceType(StringUtils.defaultString(sourceType, "OTHER"));
            record.setCreateTime(new Date());
            record.setUpdateTime(new Date());

            fileRecordDao.save(record);

            log.info("文件保存成功: code={}, originalName={}, size={} bytes", code, originalFilename, file.getSize());
            return record;
        } catch (IOException e) {
            log.error("文件保存失败", e);
            return null;
        }
    }

    /**
     * 根据文件编码读取文件信息
     * 供后端模块使用，传入编码即可获取文件记录和磁盘文件
     *
     * @param code 文件编码
     * @return 文件记录，不存在返回 null
     */
    public FileRecord getByCode(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        return fileRecordDao.getByCode(code);
    }

    /**
     * 根据文件编码获取磁盘上的文件对象
     *
     * @param code 文件编码
     * @return 磁盘文件，不存在返回 null
     */
    public File getDiskFileByCode(String code) {
        FileRecord record = getByCode(code);
        if (record == null) {
            return null;
        }
        File file = new File(uploadPath, record.getFilePath());
        if (!file.exists() || !file.isFile()) {
            log.warn("文件记录存在但磁盘文件不存在: code={}, path={}", code, record.getFilePath());
            return null;
        }
        return file;
    }

    public String getDiskFilePath(String code) {
        FileRecord record = getByCode(code);
        if (record == null) {
            return null;
        }
        return Path.of(uploadPath, record.getFilePath()).toString();
    }


    public List<File> getDiskFilesByMsgCode(String msgCode, Predicate<FileRecordDto> fileFilter) {

        if (StringUtils.isBlank(msgCode)) {
            return Collections.emptyList();
        }

        Map<String, List<FileRecordDto>> files = getFileCodesByRefCode(Lists.newArrayList(msgCode), FileRefType.CHAT_MESSAGE);
        if (MapUtils.isEmpty(files) || !files.containsKey(msgCode)) {
            return Collections.emptyList();
        }

        if (Objects.isNull(fileFilter)) {
            fileFilter = file -> true;
        }

        return files.get(msgCode).stream().filter(fileFilter).map(file -> getDiskFileByCode(file.getCode())).filter(Objects::nonNull).collect(Collectors.toList());
    }


    /**
     * 根据文件编码构建文件访问链接
     *
     * @param code 文件编码
     * @return 文件访问 URL，不存在返回 null
     */
    public String getFileUrlByCode(String code) {
        FileRecord record = getByCode(code);
        if (record == null) {
            return null;
        }
        // 通过 /xiaomizhou/opc/v1/file/access/{code} 接口访问
        return getAccessUrl(code);
    }

    /**
     *
     * @param refCode
     * @param refType   FileRefType
     * @param fileCodes
     * @return
     */
    public boolean updateRefCode(String refCode, Integer refType, List<String> fileCodes) {
        if (CollectionUtils.isEmpty(fileCodes) || StringUtils.isBlank(refCode)) {
            return false;
        }
        fileRecordDao.update(null, Wrappers.<FileRecord>lambdaUpdate().set(FileRecord::getRefCode, refCode).set(FileRecord::getRefType, refType).in(FileRecord::getCode, fileCodes));
        return true;
    }

    /**
     *
     * @param refCodes
     * @param refType
     * @return
     */
    public Map<String, List<FileRecordDto>> getFileCodesByRefCode(List<String> refCodes, Integer refType) {

        if (CollectionUtils.isEmpty(refCodes) || Objects.isNull(refType)) {
            return Map.of();
        }

        List<FileRecord> files = fileRecordDao.list(Wrappers.<FileRecord>lambdaQuery().in(FileRecord::getRefCode, refCodes).eq(FileRecord::getRefType, refType));

        Map<String, List<FileRecordDto>> map = Maps.newHashMap();

        for (FileRecord file : files) {

            String rc = file.getRefCode();

            FileRecordDto dto = new FileRecordDto();
            BeanUtils.copyProperties(file, dto);
            dto.setAccessUrl(getDownloadUrl(file));
            if (map.containsKey(rc)) {
                map.get(rc).add(dto);
            } else {
                map.put(rc, Lists.newArrayList(dto));
            }
        }

        return map;
    }

    /**
     * 根据会话编码分页查询该会话下所有分享过的文件
     * JOIN 逻辑: t_file_record.ref_type = 1 AND t_file_record.ref_code = t_chat_message.code
     *
     * @param conversationCode 会话编码
     * @param pageNo           页码
     * @param pageSize         每页大小
     * @return 分页的 FileRecordDto 列表
     */
    public Page<FileRecordDto> pageFilesByConversationCode(
            String conversationCode, int pageNo, int pageSize) {

        if (StringUtils.isBlank(conversationCode)) {
            return new Page<>(pageNo, pageSize, 0);
        }

        if (pageNo < 1) {
            pageNo = 1;
        }

        int cnt = fileRecordDao.getBaseMapper().getFileCount(conversationCode);
        if (cnt == 0) {
            return new Page<>(pageNo, pageSize, 0);
        }

        int offset = (pageNo - 1) * pageSize;
        List<FileRecord> result = fileRecordDao.getBaseMapper().selectFilesByConversationCode(conversationCode, offset, pageSize);

        Page<FileRecordDto> dtoPage = new Page<>(pageNo, pageSize, cnt);

        List<FileRecordDto> dtoList = result.stream().map(record -> {
            FileRecordDto dto = new FileRecordDto();
            BeanUtils.copyProperties(record, dto);
            dto.setAccessUrl(getDownloadUrl(record));
            return dto;
        }).collect(Collectors.toList());
        dtoPage.setRecords(dtoList);
        return dtoPage;
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

    public String getDownloadUrl(String filePath){
        return host + "uploads/" + filePath;
    }

    public String getDownloadUrl(FileRecord file){
        return host + "uploads/" + file.getFilePath();
    }

    private String getAccessUrl(String code) {
        return host + "xiaomizhou/opc/v1/file/access/" + code;
    }

}

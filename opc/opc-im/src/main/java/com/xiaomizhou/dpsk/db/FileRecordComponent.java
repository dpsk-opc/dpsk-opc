package com.xiaomizhou.dpsk.db;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiaomizhou.dpsk.db.dao.FileRecordDao;
import com.xiaomizhou.dpsk.db.dto.FileRecordDto;
import com.xiaomizhou.dpsk.db.dto.FileRecordPageCmd;
import com.xiaomizhou.dpsk.db.dto.FileRecordUpdateCmd;
import com.xiaomizhou.dpsk.db.model.FileRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * 文件记录管理组件
 *
 * @author eason - vipzhsh@163.com
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FileRecordComponent {

    private final FileRecordDao fileRecordDao;

    private final FileService fileService;

    /**
     * 管理端 - 分页查询文件记录
     */
    public ImmutablePair<Long, List<FileRecordDto>> pageFiles(FileRecordPageCmd cmd) {
        LambdaQueryWrapper<FileRecord> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(cmd.getSourceType())) {
            wrapper.eq(FileRecord::getSourceType, cmd.getSourceType());
        }
        if (StringUtils.isNotBlank(cmd.getUploaderCode())) {
            wrapper.eq(FileRecord::getUploaderCode, cmd.getUploaderCode());
        }
        if (StringUtils.isNotBlank(cmd.getOriginalName())) {
            wrapper.like(FileRecord::getOriginalName, cmd.getOriginalName());
        }

        long cnt = fileRecordDao.count(wrapper);
        if (cnt == 0) {
            return ImmutablePair.of(0L, List.of());
        }

        int pageNo = cmd.getPageNo() != null && cmd.getPageNo() > 0 ? cmd.getPageNo() : 1;
        int pageSize = cmd.getPageSize() != null && cmd.getPageSize() > 0 ? cmd.getPageSize() : 10;

        List<FileRecord> list = fileRecordDao.list(
                wrapper.last("limit %s,%s".formatted((pageNo - 1) * pageSize, pageSize))
                        .orderByDesc(FileRecord::getId));

        List<FileRecordDto> dtos = list.stream().map(record -> {
            FileRecordDto dto = new FileRecordDto();
            BeanUtils.copyProperties(record, dto);
            dto.setAccessUrl(fileService.getDownloadUrl(record.getFilePath()));
            return dto;
        }).toList();

        return ImmutablePair.of(cnt, dtos);
    }

    /**
     * 管理端 - 根据 code 查询文件记录
     */
    public FileRecordDto getByCode(String code) {
        FileRecord record = fileRecordDao.getByCode(code);
        if (record == null) {
            return null;
        }
        FileRecordDto dto = new FileRecordDto();
        BeanUtils.copyProperties(record, dto);
        dto.setFilePath(fileService.getDownloadUrl(record.getFilePath()));
        return dto;
    }

    /**
     * 管理端 - 更新文件记录元数据
     */
    public boolean update(FileRecordUpdateCmd cmd) {
        FileRecord record = fileRecordDao.getByCode(cmd.getCode());
        if (record == null) {
            return false;
        }

        FileRecord update = new FileRecord();
        update.setId(record.getId());
        if (cmd.getOriginalName() != null) {
            update.setOriginalName(cmd.getOriginalName());
        }
        if (cmd.getSourceType() != null) {
            update.setSourceType(cmd.getSourceType());
        }
        if (cmd.getRefCode() != null) {
            update.setRefCode(cmd.getRefCode());
        }
        if (cmd.getRefType() != null) {
            update.setRefType(cmd.getRefType());
        }
        update.setUpdateTime(new Date());

        return fileRecordDao.updateById(update);
    }

    /**
     * 管理端 - 根据 code 删除文件记录（逻辑删除）
     */
    public boolean deleteByCode(String code) {
        FileRecord record = fileRecordDao.getByCode(code);
        if (record == null) {
            return false;
        }
        return fileRecordDao.removeById(record.getId());
    }
}

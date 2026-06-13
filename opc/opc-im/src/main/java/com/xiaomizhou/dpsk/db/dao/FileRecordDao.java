package com.xiaomizhou.dpsk.db.dao;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaomizhou.dpsk.db.mapper.FileRecordMapper;
import com.xiaomizhou.dpsk.db.model.FileRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 文件记录 DAO
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/12
 */
@Component
@Slf4j
public class FileRecordDao extends ServiceImpl<FileRecordMapper, FileRecord> {

    /**
     * 根据文件编码查询文件记录
     *
     * @param code 文件编码
     * @return 文件记录，不存在返回 null
     */
    public FileRecord getByCode(String code) {
        return getOne(Wrappers.<FileRecord>lambdaQuery().eq(FileRecord::getCode, code));
    }

}

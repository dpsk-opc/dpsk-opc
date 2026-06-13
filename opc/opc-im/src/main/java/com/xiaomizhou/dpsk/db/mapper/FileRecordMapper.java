package com.xiaomizhou.dpsk.db.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaomizhou.dpsk.db.model.FileRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/6/12
 * @description 文件记录 Mapper
 */
public interface FileRecordMapper extends BaseMapper<FileRecord> {

    /**
     * 根据会话编码分页查询该会话下所有分享过的文件
     * JOIN 逻辑: t_file_record.ref_type = 1 AND t_file_record.ref_code = t_chat_message.code
     *
     * @param page             分页对象
     * @param conversationCode 会话编码
     * @return 分页结果
     */
    @Select("SELECT f.* FROM t_file_record f " +
            "INNER JOIN t_chat_message m ON f.ref_code = m.code AND f.ref_type = 1 " +
            "WHERE m.conversation_code = #{conversationCode} AND f.is_deleted = 0 AND m.is_deleted = 0 " +
            "ORDER BY f.create_time DESC limit #{offset},#{limit}")
    List<FileRecord> selectFilesByConversationCode(@Param("conversationCode") String conversationCode, @Param("offset") int offset, @Param("limit") int limit);


    @Select("SELECT count(1) FROM t_file_record f " +
            "INNER JOIN t_chat_message m ON f.ref_code = m.code AND f.ref_type = 1 " +
            "WHERE m.conversation_code = #{conversationCode} AND f.is_deleted = 0 AND m.is_deleted = 0")
    int getFileCount(@Param("conversationCode") String conversationCode);
}

package com.xiaomizhou.dpsk.db.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaomizhou.dpsk.db.model.TodoItemDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 待办事项 Mapper。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/24
 */
@Mapper
public interface TodoItemMapper extends BaseMapper<TodoItemDO> {
}

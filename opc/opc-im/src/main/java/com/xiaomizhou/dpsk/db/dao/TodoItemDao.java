package com.xiaomizhou.dpsk.db.dao;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaomizhou.dpsk.db.mapper.TodoItemMapper;
import com.xiaomizhou.dpsk.db.model.TodoItemDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 待办事项 DAO。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/24
 */
@Component
@Slf4j
public class TodoItemDao extends ServiceImpl<TodoItemMapper, TodoItemDO> {

    public TodoItemDO getByCode(String code) {
        return getOne(new LambdaQueryWrapper<TodoItemDO>().eq(TodoItemDO::getCode, code));
    }

}

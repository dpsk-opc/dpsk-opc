package com.xiaomizhou.dpsk.db.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaomizhou.dpsk.db.model.AgentToolRefDO;
import org.apache.ibatis.annotations.Delete;

/**
 * Agent工具绑定 Mapper。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/1
 */
public interface AgentToolRefMapper extends BaseMapper<AgentToolRefDO> {


    @Delete("delete from t_agent_tool_ref where agent_code = #{agentCode}")
    boolean hardDelByCode(String agentCode);

    @Delete("delete from t_agent_tool_ref where agent_code = #{agentCode} and tool_code = #{toolCode}")
    boolean hardDelByCodeAndToolCode(String agentCode, String toolCode);

}

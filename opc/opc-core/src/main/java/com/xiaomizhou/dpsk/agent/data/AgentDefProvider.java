package com.xiaomizhou.dpsk.agent.data;

import java.util.List;

public interface AgentDefProvider {


    AgentDef getByCode(String code);

    List<AgentDef> getByCodes(List<String> codes);


}

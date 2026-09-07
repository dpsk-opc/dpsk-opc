package com.xiaomizhou.dpsk.tool;

import com.google.common.collect.Maps;
import com.xiaomizhou.dpsk.tool.model.ToolCall;
import org.apache.commons.collections.MapUtils;

import java.util.Map;
import java.util.Objects;

public class PrivateParameterReplacer {

    private final Map<String, String> map;

    public PrivateParameterReplacer(Map<String, String> map) {
        this.map = map;
    }

    public ToolCall replace(ToolCall toolCall) {

        if (Objects.isNull(toolCall) || MapUtils.isEmpty(toolCall.getParameters())) {
            return toolCall;
        }

        ToolCall call = new ToolCall();
        call.setParameters(Maps.newHashMap(toolCall.getParameters()));
        call.setCallId(toolCall.getCallId());
        call.setName(toolCall.getName());
        call.setConfirmed(toolCall.isConfirmed());

        Map<String, Object> parameters = call.getParameters();

        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            Object value = entry.getValue();

            if(!(value instanceof String)){
                continue;
            }


            map.forEach((key, val) -> {
                if (((String) value).contains(key)) {
                    entry.setValue(((String) value).replace(key, val));
                }
            });
        }
        return call;
    }

}

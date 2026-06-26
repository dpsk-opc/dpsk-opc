package com.xiaomizhou.dpsk.workflow;

import com.yomahub.liteflow.core.NodeSwitchComponent;
import lombok.extern.slf4j.Slf4j;

import java.util.Random;

@Slf4j
public class SwitchNodeProcessor extends NodeSwitchComponent {

    int max = 5;

    @Override
    public String processSwitch() throws Exception {
        log.info("switch node execute!nodeId:{}", getNodeId());

        for (int i = 0; i < max; i++) {
            if (new Random().nextInt(10) > 5) {
                return "e";
            }
            return "a";
        }

        throw new RuntimeException("switch node execute error!");
    }
}

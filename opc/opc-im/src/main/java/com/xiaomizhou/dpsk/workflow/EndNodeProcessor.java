package com.xiaomizhou.dpsk.workflow;

import com.yomahub.liteflow.core.NodeComponent;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EndNodeProcessor extends NodeComponent {

    @Override
    public void process() throws Exception {
        log.info("start node execute!nodeId:{}", getNodeId());
    }

}

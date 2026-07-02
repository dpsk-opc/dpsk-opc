package com.xiaomizhou.dpsk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.Maps;
import com.xiaomizhou.dpsk.utils.JsonUtils;
import com.xiaomizhou.dpsk.workflow.AgentNodeProcessor;
import com.xiaomizhou.dpsk.workflow.EndNodeProcessor;
import com.xiaomizhou.dpsk.workflow.StartNodeProcessor;
import com.xiaomizhou.dpsk.workflow.SwitchNodeProcessor;
import com.xiaomizhou.dpsk.workflow.xyflow.XyFlow;
import com.xiaomizhou.dpsk.workflow.xyflow.XyFlowToLiteFlowUtils;
import com.yomahub.liteflow.builder.LiteFlowNodeBuilder;
import com.yomahub.liteflow.builder.el.LiteFlowChainELBuilder;
import com.yomahub.liteflow.core.FlowExecutor;
import com.yomahub.liteflow.property.LiteflowConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Map;

@Slf4j
public class WorkflowUtilsTests {


    @Test
    public void toElTest() throws JsonProcessingException {


        XyFlow flow = JsonUtils.toObj("{\"steps\":[{\"id\":\"start_1782380476065\",\"type\":\"start\",\"label\":\"开始\",\"order\":1,\"x\":200,\"y\":400},{\"id\":\"process_1782380483404_a6jq\",\"type\":\"process\",\"label\":\"路易斯\",\"order\":2,\"x\":400,\"y\":380,\"agentCode\":\"AGT-0954249de54f4458bc59167ec1889bcb\",\"agentName\":\"路易斯\",\"agentAvatar\":\"http://127.0.0.1:8080/uploads/2026/06/20/20260620174542_70375905acdc47e4854e04bda8075789.jpg\",\"agentModel\":\"deepseek-v4-flash\",\"mcpCodes\":[],\"skillPaths\":[]},{\"id\":\"process_1782380489512_qore\",\"type\":\"process\",\"label\":\"刘小花\",\"order\":3,\"x\":660,\"y\":280,\"agentCode\":\"AGT-27546edc533441e4ab65c1a119388c4a\",\"agentName\":\"刘小花\",\"agentAvatar\":\"http://127.0.0.1:8080/uploads/2026/06/12/20260612005056_a98d9ac2be2f4bd2a5ec7b875dffd6a7.jpg\",\"agentModel\":\"deepseek-v4-flash\",\"mcpCodes\":[],\"skillPaths\":[]},{\"id\":\"process_1782380493070_smg0\",\"type\":\"process\",\"label\":\"卡特\",\"order\":4,\"x\":720,\"y\":440,\"agentCode\":\"AGT-a25c24fb3d6a4f3290a63d98d8e6cc80\",\"agentName\":\"卡特\",\"agentAvatar\":\"\",\"agentModel\":\"agnes-2.0-flash\",\"mcpCodes\":[],\"skillPaths\":[]},{\"id\":\"confirm_1782380497440_3eso\",\"type\":\"confirm\",\"label\":\"人工确认\",\"order\":5,\"x\":920,\"y\":280},{\"id\":\"end_1782392914092_cto1\",\"type\":\"end\",\"label\":\"结束\",\"order\":6,\"x\":1180,\"y\":480}],\"edges\":[{\"id\":\"edge_start_1782380476065_process_1782380483404_a6jq\",\"source\":\"start_1782380476065\",\"target\":\"process_1782380483404_a6jq\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"\",\"label\":\"\"},{\"id\":\"edge_process_1782380483404_a6jq_process_1782380489512_qore\",\"source\":\"process_1782380483404_a6jq\",\"target\":\"process_1782380489512_qore\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"{{age}} > 18\",\"label\":\"是\"},{\"id\":\"edge_process_1782380483404_a6jq_process_1782380493070_smg0\",\"source\":\"process_1782380483404_a6jq\",\"target\":\"process_1782380493070_smg0\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"{{age}} <= 18\",\"label\":\"否\"},{\"id\":\"edge_process_1782380489512_qore_confirm_1782380497440_3eso\",\"source\":\"process_1782380489512_qore\",\"target\":\"confirm_1782380497440_3eso\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"\",\"label\":\"\"},{\"id\":\"edge_confirm_1782380497440_3eso_end_1782392914092_cto1\",\"source\":\"confirm_1782380497440_3eso\",\"target\":\"end_1782392914092_cto1\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"\",\"label\":\"\"},{\"id\":\"edge_process_1782380493070_smg0_end_1782392914092_cto1\",\"source\":\"process_1782380493070_smg0\",\"target\":\"end_1782392914092_cto1\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"\",\"label\":\"\"}]}", XyFlow.class);

        String el = XyFlowToLiteFlowUtils.toEl(flow);
        log.info("simple el expression:{}", el);


        XyFlow hard = JsonUtils.toObj("{\"steps\":[{\"id\":\"process_1782399142971_xiad\",\"type\":\"process\",\"label\":\"路易斯\",\"order\":1,\"x\":240,\"y\":360,\"agentCode\":\"AGT-0954249de54f4458bc59167ec1889bcb\",\"agentName\":\"路易斯\",\"agentAvatar\":\"http://127.0.0.1:8080/uploads/2026/06/20/20260620174542_70375905acdc47e4854e04bda8075789.jpg\",\"agentModel\":\"deepseek-v4-flash\",\"mcpCodes\":[],\"skillPaths\":[]},{\"id\":\"process_1782399150642_bqaf\",\"type\":\"process\",\"label\":\"UI设计师\",\"order\":2,\"x\":480,\"y\":200,\"agentCode\":\"AGT-84e0c01efeb143008aa569746ced2203\",\"agentName\":\"UI设计师\",\"agentAvatar\":\"http://127.0.0.1:8080/uploads/2026/06/12/20260612005555_133c7342ade243afb97f2c32d12e4258.jpg\",\"agentModel\":\"agnes-2.0-flash\",\"mcpCodes\":[],\"skillPaths\":[]},{\"id\":\"process_1782399153918_qw1e\",\"type\":\"process\",\"label\":\"卡特\",\"order\":3,\"x\":760,\"y\":200,\"agentCode\":\"AGT-a25c24fb3d6a4f3290a63d98d8e6cc80\",\"agentName\":\"卡特\",\"agentAvatar\":\"\",\"agentModel\":\"agnes-2.0-flash\",\"mcpCodes\":[],\"skillPaths\":[]},{\"id\":\"end_1782399184961_xa9a\",\"type\":\"end\",\"label\":\"结束\",\"order\":4,\"x\":1160,\"y\":420},{\"id\":\"start_1782399203263_hmes\",\"type\":\"start\",\"label\":\"开始\",\"order\":5,\"x\":60,\"y\":360},{\"id\":\"process_1782399226851_rz39\",\"type\":\"process\",\"label\":\"刘小花\",\"order\":6,\"x\":480,\"y\":480,\"agentCode\":\"AGT-27546edc533441e4ab65c1a119388c4a\",\"agentName\":\"刘小花\",\"agentAvatar\":\"http://127.0.0.1:8080/uploads/2026/06/12/20260612005056_a98d9ac2be2f4bd2a5ec7b875dffd6a7.jpg\",\"agentModel\":\"deepseek-v4-flash\",\"mcpCodes\":[],\"skillPaths\":[]},{\"id\":\"process_1782399231043_dc70\",\"type\":\"process\",\"label\":\"UI设计师\",\"order\":7,\"x\":760,\"y\":400,\"agentCode\":\"AGT-84e0c01efeb143008aa569746ced2203\",\"agentName\":\"UI设计师\",\"agentAvatar\":\"http://127.0.0.1:8080/uploads/2026/06/12/20260612005555_133c7342ade243afb97f2c32d12e4258.jpg\",\"agentModel\":\"agnes-2.0-flash\",\"mcpCodes\":[],\"skillPaths\":[]},{\"id\":\"process_1782399238651_42p8\",\"type\":\"process\",\"label\":\"嘴炮辩论者\",\"order\":8,\"x\":760,\"y\":540,\"agentCode\":\"AGT-ed91220de76745dba42e2e190dca1a2a\",\"agentName\":\"嘴炮辩论者\",\"agentAvatar\":\"http://127.0.0.1:8080/uploads/2026/06/12/20260612125826_7d137902203843918f4a0b4b68de18ce.jpg\",\"agentModel\":\"qwen-max\",\"mcpCodes\":[],\"skillPaths\":[]}],\"edges\":[{\"id\":\"edge_process_1782399142971_xiad_process_1782399150642_bqaf\",\"source\":\"process_1782399142971_xiad\",\"target\":\"process_1782399150642_bqaf\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"1\",\"label\":\"1\"},{\"id\":\"edge_process_1782399150642_bqaf_process_1782399153918_qw1e\",\"source\":\"process_1782399150642_bqaf\",\"target\":\"process_1782399153918_qw1e\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"\",\"label\":\"\"},{\"id\":\"edge_process_1782399153918_qw1e_end_1782399184961_xa9a\",\"source\":\"process_1782399153918_qw1e\",\"target\":\"end_1782399184961_xa9a\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"1\",\"label\":\"1\"},{\"id\":\"edge_start_1782399203263_hmes_process_1782399142971_xiad\",\"source\":\"start_1782399203263_hmes\",\"target\":\"process_1782399142971_xiad\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"\",\"label\":\"\"},{\"id\":\"edge_process_1782399142971_xiad_process_1782399226851_rz39\",\"source\":\"process_1782399142971_xiad\",\"target\":\"process_1782399226851_rz39\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"1\",\"label\":\"1\"},{\"id\":\"edge_process_1782399226851_rz39_process_1782399231043_dc70\",\"source\":\"process_1782399226851_rz39\",\"target\":\"process_1782399231043_dc70\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"2\",\"label\":\"2\"},{\"id\":\"edge_process_1782399226851_rz39_process_1782399238651_42p8\",\"source\":\"process_1782399226851_rz39\",\"target\":\"process_1782399238651_42p8\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"1\",\"label\":\"1\"},{\"id\":\"edge_process_1782399231043_dc70_end_1782399184961_xa9a\",\"source\":\"process_1782399231043_dc70\",\"target\":\"end_1782399184961_xa9a\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"\",\"label\":\"\"},{\"id\":\"edge_process_1782399238651_42p8_end_1782399184961_xa9a\",\"source\":\"process_1782399238651_42p8\",\"target\":\"end_1782399184961_xa9a\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"\",\"label\":\"\"}]}", XyFlow.class);
        String hardEl = XyFlowToLiteFlowUtils.toEl(hard);
        log.info("hard el expression:{}", hardEl);


        XyFlow seq = JsonUtils.toObj("{\"steps\":[{\"id\":\"start_1782400051449\",\"type\":\"start\",\"label\":\"开始\",\"order\":1,\"x\":20,\"y\":180},{\"id\":\"process_1782400055458_1o1l\",\"type\":\"process\",\"label\":\"路易斯\",\"order\":2,\"x\":220,\"y\":320,\"agentCode\":\"AGT-0954249de54f4458bc59167ec1889bcb\",\"agentName\":\"路易斯\",\"agentAvatar\":\"http://127.0.0.1:8080/uploads/2026/06/20/20260620174542_70375905acdc47e4854e04bda8075789.jpg\",\"agentModel\":\"deepseek-v4-flash\",\"mcpCodes\":[],\"skillPaths\":[]},{\"id\":\"process_1782400058409_cfgi\",\"type\":\"process\",\"label\":\"刘小花\",\"order\":3,\"x\":480,\"y\":400,\"agentCode\":\"AGT-27546edc533441e4ab65c1a119388c4a\",\"agentName\":\"刘小花\",\"agentAvatar\":\"http://127.0.0.1:8080/uploads/2026/06/12/20260612005056_a98d9ac2be2f4bd2a5ec7b875dffd6a7.jpg\",\"agentModel\":\"deepseek-v4-flash\",\"mcpCodes\":[],\"skillPaths\":[]},{\"id\":\"process_1782400062252_0j9c\",\"type\":\"process\",\"label\":\"UI设计师\",\"order\":4,\"x\":760,\"y\":300,\"agentCode\":\"AGT-84e0c01efeb143008aa569746ced2203\",\"agentName\":\"UI设计师\",\"agentAvatar\":\"http://127.0.0.1:8080/uploads/2026/06/12/20260612005555_133c7342ade243afb97f2c32d12e4258.jpg\",\"agentModel\":\"agnes-2.0-flash\",\"mcpCodes\":[],\"skillPaths\":[]},{\"id\":\"end_1782400067154_zib3\",\"type\":\"end\",\"label\":\"结束\",\"order\":5,\"x\":1120,\"y\":300}],\"edges\":[{\"id\":\"edge_start_1782400051449_process_1782400055458_1o1l\",\"source\":\"start_1782400051449\",\"target\":\"process_1782400055458_1o1l\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"\",\"label\":\"\"},{\"id\":\"edge_process_1782400055458_1o1l_process_1782400058409_cfgi\",\"source\":\"process_1782400055458_1o1l\",\"target\":\"process_1782400058409_cfgi\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"\",\"label\":\"\",\"maxIterations\":1},{\"id\":\"edge_process_1782400058409_cfgi_process_1782400062252_0j9c\",\"source\":\"process_1782400058409_cfgi\",\"target\":\"process_1782400062252_0j9c\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"\",\"label\":\"\",\"maxIterations\":1},{\"id\":\"edge_process_1782400062252_0j9c_end_1782400067154_zib3\",\"source\":\"process_1782400062252_0j9c\",\"target\":\"end_1782400067154_zib3\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"1\",\"label\":\"\"}]}", XyFlow.class);
        String seqEl = XyFlowToLiteFlowUtils.toEl(seq);
        log.info("seq el expression:{}", seqEl);

        XyFlow sw4 = JsonUtils.toObj("{\"steps\":[{\"id\":\"start_1782400344241\",\"type\":\"start\",\"label\":\"开始\",\"order\":1,\"x\":-80,\"y\":300},{\"id\":\"process_1782400348153_qhjq\",\"type\":\"process\",\"label\":\"路易斯\",\"order\":2,\"x\":120,\"y\":360,\"agentCode\":\"AGT-0954249de54f4458bc59167ec1889bcb\",\"agentName\":\"路易斯\",\"agentAvatar\":\"http://127.0.0.1:8080/uploads/2026/06/20/20260620174542_70375905acdc47e4854e04bda8075789.jpg\",\"agentModel\":\"deepseek-v4-flash\",\"mcpCodes\":[],\"skillPaths\":[]},{\"id\":\"process_1782400353489_qorq\",\"type\":\"process\",\"label\":\"UI设计师\",\"order\":3,\"x\":400,\"y\":240,\"agentCode\":\"AGT-84e0c01efeb143008aa569746ced2203\",\"agentName\":\"UI设计师\",\"agentAvatar\":\"http://127.0.0.1:8080/uploads/2026/06/12/20260612005555_133c7342ade243afb97f2c32d12e4258.jpg\",\"agentModel\":\"agnes-2.0-flash\",\"mcpCodes\":[],\"skillPaths\":[]},{\"id\":\"process_1782400357187_qpmm\",\"type\":\"process\",\"label\":\"卡特\",\"order\":4,\"x\":400,\"y\":340,\"agentCode\":\"AGT-a25c24fb3d6a4f3290a63d98d8e6cc80\",\"agentName\":\"卡特\",\"agentAvatar\":\"\",\"agentModel\":\"agnes-2.0-flash\",\"mcpCodes\":[],\"skillPaths\":[]},{\"id\":\"process_1782400360327_f5ga\",\"type\":\"process\",\"label\":\"嘴炮辩论者\",\"order\":5,\"x\":400,\"y\":460,\"agentCode\":\"AGT-ed91220de76745dba42e2e190dca1a2a\",\"agentName\":\"嘴炮辩论者\",\"agentAvatar\":\"http://127.0.0.1:8080/uploads/2026/06/12/20260612125826_7d137902203843918f4a0b4b68de18ce.jpg\",\"agentModel\":\"qwen-max\",\"mcpCodes\":[],\"skillPaths\":[]},{\"id\":\"process_1782400369164_gm4a\",\"type\":\"process\",\"label\":\"刘小花\",\"order\":6,\"x\":400,\"y\":160,\"agentCode\":\"AGT-27546edc533441e4ab65c1a119388c4a\",\"agentName\":\"刘小花\",\"agentAvatar\":\"http://127.0.0.1:8080/uploads/2026/06/12/20260612005056_a98d9ac2be2f4bd2a5ec7b875dffd6a7.jpg\",\"agentModel\":\"deepseek-v4-flash\",\"mcpCodes\":[],\"skillPaths\":[]},{\"id\":\"process_1782400373977_rfwe\",\"type\":\"process\",\"label\":\"UI设计师\",\"order\":7,\"x\":640,\"y\":160,\"agentCode\":\"AGT-84e0c01efeb143008aa569746ced2203\",\"agentName\":\"UI设计师\",\"agentAvatar\":\"http://127.0.0.1:8080/uploads/2026/06/12/20260612005555_133c7342ade243afb97f2c32d12e4258.jpg\",\"agentModel\":\"agnes-2.0-flash\",\"mcpCodes\":[],\"skillPaths\":[]},{\"id\":\"end_1782400378919_xqw9\",\"type\":\"end\",\"label\":\"结束\",\"order\":8,\"x\":840,\"y\":360}],\"edges\":[{\"id\":\"edge_start_1782400344241_process_1782400348153_qhjq\",\"source\":\"start_1782400344241\",\"target\":\"process_1782400348153_qhjq\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"\",\"label\":\"\"},{\"id\":\"edge_process_1782400348153_qhjq_process_1782400353489_qorq\",\"source\":\"process_1782400348153_qhjq\",\"target\":\"process_1782400353489_qorq\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"2\",\"label\":\"\"},{\"id\":\"edge_process_1782400348153_qhjq_process_1782400357187_qpmm\",\"source\":\"process_1782400348153_qhjq\",\"target\":\"process_1782400357187_qpmm\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"3\",\"label\":\"\"},{\"id\":\"edge_process_1782400348153_qhjq_process_1782400360327_f5ga\",\"source\":\"process_1782400348153_qhjq\",\"target\":\"process_1782400360327_f5ga\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"4\",\"label\":\"\"},{\"id\":\"edge_process_1782400348153_qhjq_process_1782400369164_gm4a\",\"source\":\"process_1782400348153_qhjq\",\"target\":\"process_1782400369164_gm4a\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"1\",\"label\":\"\"},{\"id\":\"edge_process_1782400369164_gm4a_process_1782400373977_rfwe\",\"source\":\"process_1782400369164_gm4a\",\"target\":\"process_1782400373977_rfwe\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"\",\"label\":\"\"},{\"id\":\"edge_process_1782400373977_rfwe_end_1782400378919_xqw9\",\"source\":\"process_1782400373977_rfwe\",\"target\":\"end_1782400378919_xqw9\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"\",\"label\":\"\"},{\"id\":\"edge_process_1782400353489_qorq_end_1782400378919_xqw9\",\"source\":\"process_1782400353489_qorq\",\"target\":\"end_1782400378919_xqw9\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"\",\"label\":\"\"},{\"id\":\"edge_process_1782400357187_qpmm_end_1782400378919_xqw9\",\"source\":\"process_1782400357187_qpmm\",\"target\":\"end_1782400378919_xqw9\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"\",\"label\":\"\"},{\"id\":\"edge_process_1782400360327_f5ga_end_1782400378919_xqw9\",\"source\":\"process_1782400360327_f5ga\",\"target\":\"end_1782400378919_xqw9\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"\",\"label\":\"\"}]}", XyFlow.class);
        String sw4El = XyFlowToLiteFlowUtils.toEl(sw4);
        log.info("sw4 el expression:{}", sw4El);


        XyFlow sw5 = JsonUtils.toObj("{\"steps\":[{\"id\":\"start_1782800873510\",\"type\":\"start\",\"label\":\"开始\",\"order\":1,\"x\":-160,\"y\":220},{\"id\":\"process_1782800880905_l4im\",\"type\":\"process\",\"label\":\"路易斯\",\"order\":2,\"x\":40,\"y\":240,\"agentCode\":\"AGT-0954249de54f4458bc59167ec1889bcb\",\"agentName\":\"路易斯\",\"agentAvatar\":\"http://127.0.0.1:8080/uploads/2026/06/20/20260620174542_70375905acdc47e4854e04bda8075789.jpg\",\"agentModel\":\"deepseek-v4-flash\",\"mcpCodes\":[],\"skillPaths\":[],\"systemPrompt\":\"\"},{\"id\":\"process_1782800886453_c66f\",\"type\":\"process\",\"label\":\"刘小花\",\"order\":3,\"x\":320,\"y\":140,\"agentCode\":\"AGT-27546edc533441e4ab65c1a119388c4a\",\"agentName\":\"刘小花\",\"agentAvatar\":\"http://127.0.0.1:8080/uploads/2026/06/12/20260612005056_a98d9ac2be2f4bd2a5ec7b875dffd6a7.jpg\",\"agentModel\":\"deepseek-v4-flash\",\"mcpCodes\":[],\"skillPaths\":[],\"systemPrompt\":\"将用户的输入，按照时间和类比保存到电脑上。目录:W:\\\\w\\\\workspace\\\\dpsk-opc\\\\\"},{\"id\":\"end_1782800941314_a8va\",\"type\":\"end\",\"label\":\"结束\",\"order\":4,\"x\":620,\"y\":240},{\"id\":\"process_1782801578031_7w4x\",\"type\":\"process\",\"label\":\"路易斯\",\"order\":5,\"x\":320,\"y\":340,\"agentCode\":\"AGT-0954249de54f4458bc59167ec1889bcb\",\"agentName\":\"路易斯\",\"agentAvatar\":\"http://127.0.0.1:8080/uploads/2026/06/20/20260620174542_70375905acdc47e4854e04bda8075789.jpg\",\"agentModel\":\"deepseek-v4-flash\",\"mcpCodes\":[],\"skillPaths\":[],\"systemPrompt\":\"将输入的信息改写成适合小孩子阅读的版本\"}],\"edges\":[{\"id\":\"edge_start_1782800873510_process_1782800880905_l4im\",\"source\":\"start_1782800873510\",\"target\":\"process_1782800880905_l4im\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"\",\"label\":\"\"},{\"id\":\"edge_process_1782800880905_l4im_process_1782800886453_c66f\",\"source\":\"process_1782800880905_l4im\",\"target\":\"process_1782800886453_c66f\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"鬼怪，玄幻等不适合小孩子阅读的内容\",\"label\":\"成人读物\"},{\"id\":\"edge_process_1782800886453_c66f_end_1782800941314_a8va\",\"source\":\"process_1782800886453_c66f\",\"target\":\"end_1782800941314_a8va\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"\",\"label\":\"\"},{\"id\":\"edge_process_1782800880905_l4im_process_1782801578031_7w4x\",\"source\":\"process_1782800880905_l4im\",\"target\":\"process_1782801578031_7w4x\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"儿童读物\",\"label\":\"儿童读物\"},{\"id\":\"edge_process_1782801578031_7w4x_end_1782800941314_a8va\",\"source\":\"process_1782801578031_7w4x\",\"target\":\"end_1782800941314_a8va\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"\",\"label\":\"\"}]}", XyFlow.class);
        String sw5El = XyFlowToLiteFlowUtils.toEl(sw5);
        log.info("sw5 el expression:{}", sw5El);


    }

    @Test
    @Ignore
    public void testOnLoop() {

        /**
         * a ---> b ---> d ---> e
         *  <---         |
         */

        LiteFlowNodeBuilder.createCommonNode().setId("a")
                .setName("a")
                .setClazz(StartNodeProcessor.class)
                .build();


        LiteFlowNodeBuilder.createCommonNode().setId("b")
                .setName("b")
                .setClazz(AgentNodeProcessor.class)
                .build();

        LiteFlowNodeBuilder.createSwitchNode().setId("d")
                .setName("d")
                .setClazz(SwitchNodeProcessor.class)
                .build();


        LiteFlowNodeBuilder.createCommonNode().setId("e")
                .setName("e")
                .setClazz(EndNodeProcessor.class)
                .build();


        LiteFlowChainELBuilder.createChain().setChainId("chain2").setEL(
                // 输出el表达式
                "THEN(a,b);"
//                "THEN(a,b,IF(d,e).ELSE(a),e)"
//                "SWITCH(d).to(a,e).DEFAULT(b)"
        ).build();

        LiteflowConfig config = new LiteflowConfig();

        config.setChainCacheEnabled(false);
        config.setSupportMultipleType(false);
        config.setEnableMonitorFile(true);
        config.setEnableLog(true);

        FlowExecutor executor = new FlowExecutor(config);

        Map<String, Object> map = Maps.newHashMap();

        map.put("a", "a");
        map.put("b", "b");


//        WorkflowContext context = WorkflowContext.builder()
//                .build();
//
//        LiteflowResponse response = executor.execute2Resp("chain2", map, NodeContext.builder()
//                .nodeAgentCode("aaa")
//                .nodeId("a")
//                .nodePrompt("ssss")
//                .build());

//        log.info("executor:{}",response);
    }

}

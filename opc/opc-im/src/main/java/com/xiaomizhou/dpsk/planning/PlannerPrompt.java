package com.xiaomizhou.dpsk.planning;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 规划 Prompt 构造器。
 *
 * <p>输出为符合 {@code WorkflowPlanJson} 的 JSON，由 {@link WorkflowPlanner} 解析为
 * {@code XyFlow}，再交给 {@link PlanValidator} 做结构性校验。
 */
public final class PlannerPrompt {

    private PlannerPrompt() {
    }

    /**
     * 构造规划 Prompt。
     *
     * @param agentPoolTexts 候选 Agent 池文本列表，每项已含 code 与能力/人设描述（由调用方拼接）
     * @param histories      replan 历史节点结果；首次规划为空列表
     * @param userRequest    用户原始诉求
     * @param isReplan       是否 replan（影响指令措辞）
     */
    public static String build(List<String> agentPoolTexts, List<PlanningRequest.NodeResultRef> histories,
                               String userRequest, boolean isReplan) {
        String agentPool = agentPoolTexts.stream()
                .map(c -> "- " + c)
                .collect(Collectors.joining("\n"));

        StringBuilder historyText = new StringBuilder();
        if (histories != null && !histories.isEmpty()) {
            historyText.append("已完成的节点及其输出（请基于它们规划，避免重复）：\n");
            for (PlanningRequest.NodeResultRef ref : histories) {
                historyText.append("  nodeId=").append(ref.getNodeId()).append(", output=")
                        .append(ref.getOutput() == null ? "" : truncate(ref.getOutput(), 500)).append("\n");
            }
        } else {
            historyText.append("无历史节点结果。\n");
        }

        String replanHint = isReplan
                ? "【本次为 replan】上一次规划/执行存在失败或路径不当，请结合失败信息修正节点顺序与任务分派，确保本次可一次跑通。"
                : "请第一次为该诉求做整体规划。";

        return """
                你是资深的多 Agent 工作流编排器。请根据用户的诉求，在给定的 Agent 候选池中挑选【必要】的 Agent，编排成一个有向无环的工作流图（DAG），并输出严格的 JSON。

                【按需挑选，不要全用】
                - 候选池只是"可用成员"，不是"必须全部上场"。只挑选真正能帮助完成本次诉求的 Agent，其余一律不用。
                - Agent 数量应与任务复杂度匹配：简单任务派 1 个 Agent 即可；只有需要多角色分工/多步骤协作时才派多个。
                - 不要为了"让更多人参与"而强行把每个候选 Agent 都编排进图。宁可少而精准，也不要冗余。
                - 若某 Agent 与本次诉求无关，即使它在候选池里，也不得使用。

                约束（必须遵守）：
                1. 必须包含且仅包含 1 个 start 节点、1 个 end 节点；start 与 end 之间至少 1 个 agent 节点。
                2. 所有非 start/end 节点的 type 必须严格取候选 Agent 池条目中括号内的 code，例如 "type": "xxx"。请根据每个 Agent 的能力/人设描述挑选最合适的，不得使用候选池之外的 code。
                3. 除 switch 节点外，不允许出现分叉/汇聚（DAG 主链）；switch 节点的多条出边 target 必须与后续节点对应。
                4. 不允许出现环（节点不能回到已访问节点）。
                5. 每个节点的 task 字段需给出该 Agent 的明确任务指令（中文，具体到该 Agent 的能力范围）。
                6. 条件分支（switch）：type 填 "switch"，task 填分支判断规则（中文，说明在何种结论下进入哪一条出边）；分支的出边用 edges 表达，condition 字段描述该分支的进入条件。
                7. 所有节点的 id 使用形如 n1,n2,n3... 的稳定编号；edges 用 source/target 引用节点 id。

                候选 Agent 池（括号内为该 Agent 的 code，节点的 type 必须用这个 code；只用其中必要的，不要全部使用）：
                %s

                用户诉求：
                %s

                历史节点结果：
                %s
                %s

                请仅输出如下结构的 JSON（不要包含任何解释或 markdown 代码块标记）。
                简单任务（单个 Agent 即可完成）示例：
                {
                  "nodes": [
                    {"id": "n1", "type": "start", "label": "", "task": ""},
                    {"id": "n2", "type": "<候选Agent code>", "label": "节点名", "task": "任务指令"},
                    {"id": "n3", "type": "end", "label": "", "task": ""}
                  ],
                  "edges": [
                    {"source": "n1", "target": "n2", "condition": "", "label": ""},
                    {"source": "n2", "target": "n3", "condition": "", "label": ""}
                  ]
                }
                复杂任务（需多 Agent 协作 / 条件分支）示例：
                {
                  "nodes": [
                    {"id": "n1", "type": "start", "label": "", "task": ""},
                    {"id": "n2", "type": "<候选Agent code>", "label": "节点名", "task": "任务指令"},
                    {"id": "n3", "type": "switch", "label": "", "task": "判断规则"},
                    {"id": "n4", "type": "<候选Agent code>", "label": "节点名", "task": "任务指令"},
                    {"id": "nN", "type": "end", "label": "", "task": ""}
                  ],
                  "edges": [
                    {"source": "n1", "target": "n2", "condition": "", "label": ""},
                    {"source": "n2", "target": "n3", "condition": "", "label": ""},
                    {"source": "n3", "target": "n4", "condition": "当结论为X时", "label": ""},
                    {"source": "n3", "target": "nN", "condition": "当结论为Y时", "label": ""}
                  ]
                }
                """.formatted(agentPool, userRequest, historyText, replanHint);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}

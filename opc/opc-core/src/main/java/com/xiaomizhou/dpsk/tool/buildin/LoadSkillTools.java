package com.xiaomizhou.dpsk.tool.buildin;

import com.xiaomizhou.dpsk.tool.ToolMeta;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.skills.FileSystemSkill;
import dev.langchain4j.skills.FileSystemSkillLoader;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Path;
import java.util.Objects;

import static com.xiaomizhou.dpsk.tool.model.ToolMetadata.RISK_NORMAL;

/**
 * 加载内置技能工具。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/12
 */
@ToolMeta(value = "加载skills", level = RISK_NORMAL)
public class LoadSkillTools {


    @Tool(name = "load_skills", value = "加载内置技能")
    public String loadSkills(@P(description = "技能路径") String skillPath) {
        if (StringUtils.isBlank(skillPath)) {
            return "skillPath is empty";
        }
        try {
            FileSystemSkill skill = FileSystemSkillLoader.loadSkill(Path.of(skillPath));
            if (Objects.isNull(skill)) {
                return "no any skills under the path.";
            }
            return skill.toString();
        } catch (Exception e) {
            return "加载文件出错!" + e.getMessage();
        }
    }

}

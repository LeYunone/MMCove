package com.mmcove.agent.vibe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmcove.agent.common.model.entity.PipelineStage;
import com.mmcove.agent.common.model.entity.PipelineStageStep;
import com.mmcove.agent.common.model.entity.PipelineTemplate;
import com.mmcove.agent.infra.persistence.repository.PipelineStageRepository;
import com.mmcove.agent.infra.persistence.repository.PipelineStageStepRepository;
import com.mmcove.agent.infra.persistence.repository.PipelineTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流水线 YAML 交换格式服务:人可读的导入/导出(「融汇贯通」的最轻载体——
 * 一条流水线就是一段可版本化、可分享的 YAML)。
 *
 * <p>支持 extends 继承语法,导入时<b>物化</b>(展开成完整 steps 落库):
 * 库内不存继承关系,运行时零额外逻辑。导出为自包含 YAML(不携带 extends)。
 *
 * <p>受限子集格式:
 * <pre>
 * pipeline: bug-fix-strict
 * name: 严格缺陷修复流水线
 * intent: BUG_FIX
 * extends: bug-fix            # 可选:继承基线流水线的阶段序列与 keywords
 * keywords: 注入,安全,漏洞      # 可选:追加到继承的 keywords
 * convention: ...             # 可选:执行约定(缺省用服务端内置)
 * stages+:                    # 可选:在基线序列上增阶段
 *   - code: security-audit
 *     after: root-cause       # 可选:插在某阶段后(缺省=序列尾)
 *     params: {subAgentCount: 2}
 * stages-:                    # 可选:从基线序列剔除
 *   - regression-test
 * </pre>
 *
 * @since 2026-09-11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineYamlService {

    private final PipelineTemplateRepository pipelineTemplateRepository;
    private final PipelineStageRepository pipelineStageRepository;
    private final PipelineStageStepRepository pipelineStageStepRepository;
    private final PipelineRegistry pipelineRegistry;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 导入结果。
     */
    public record ImportResult(Long pipelineId, String pipelineCode, int stageCount) {
    }

    /** 导出为自包含 YAML(全量阶段序列物化,不携带 extends) */
    public String exportToYaml(Long pipelineId) {
        PipelineTemplate template = pipelineTemplateRepository.findById(pipelineId)
                .orElseThrow(() -> new IllegalArgumentException("流水线不存在: " + pipelineId));
        List<PipelineStageStep> steps = pipelineStageStepRepository.findByPipelineIdOrderBySeq(pipelineId);
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("流水线 [" + template.getName() + "] 未配置阶段序列");
        }
        Map<Long, PipelineStage> stageById = stageMap();

        StringBuilder sb = new StringBuilder(512);
        sb.append("pipeline: ").append(template.getPipelineCode()).append('\n');
        sb.append("name: ").append(template.getName()).append('\n');
        sb.append("intent: ").append(template.getIntentType()).append('\n');
        if (notBlank(template.getDescription())) {
            sb.append("description: ").append(template.getDescription()).append('\n');
        }
        if (notBlank(template.getKeywords())) {
            sb.append("keywords: ").append(template.getKeywords()).append('\n');
        }
        if (notBlank(template.getExecutionConvention())) {
            sb.append("convention: |\n");
            for (String line : template.getExecutionConvention().split("\n")) {
                sb.append("  ").append(line).append('\n');
            }
        }
        sb.append("stages:\n");
        for (PipelineStageStep step : steps) {
            PipelineStage stage = stageById.get(step.getStageId());
            if (stage == null) {
                continue;
            }
            sb.append("  - code: ").append(stage.getStageCode()).append('\n');
            if (step.getStepParams() != null && !step.getStepParams().isBlank()
                    && !"null".equals(step.getStepParams())) {
                sb.append("    params: ").append(step.getStepParams()).append('\n');
            }
        }
        return sb.toString();
    }

    /** 解析 YAML → 校验 → extends 物化 → 落库(模板 + steps replaceAll) */
    @Transactional
    public ImportResult importFromYaml(String yamlText) {
        Map<String, Object> root = parseYaml(yamlText);
        String code = requireText(root, "pipeline");
        String name = requireText(root, "name");
        String intent = requireText(root, "intent");

        if (pipelineTemplateRepository.findByCode(code).isPresent()) {
            throw new IllegalArgumentException("流水线编码已存在: " + code + "(导出修改后请更换 pipeline 编码)");
        }

        Map<Long, PipelineStage> stageById = stageMap();

        // 阶段序列:extends 基线(物化) → 应用 stages- → 应用 stages+
        List<SeqEntry> sequence = baseSequence(root, stageById);
        applyRemovals(root, sequence);
        applyAdditions(root, stageById, sequence);
        if (sequence.isEmpty()) {
            throw new IllegalArgumentException("阶段序列为空(无 extends 且未配置 stages/stages+)");
        }

        // keywords: 继承基线 + 本段追加(去重)
        String keywords = mergeKeywords(root);

        PipelineTemplate template = new PipelineTemplate();
        template.setPipelineCode(code);
        template.setName(name);
        template.setIntentType(intent);
        template.setDescription(str(root.get("description")));
        template.setKeywords(keywords);
        template.setExecutionConvention(str(root.get("convention")));
        template.setStatus(1);
        pipelineTemplateRepository.insert(template);

        List<PipelineStageStep> steps = new ArrayList<>();
        int seq = 10;
        for (SeqEntry entry : sequence) {
            PipelineStageStep step = new PipelineStageStep();
            step.setStageId(entry.stageId());
            step.setSeq(seq);
            step.setStepParams(entry.params());
            steps.add(step);
            seq += 10;
        }
        pipelineStageStepRepository.replaceAll(template.getId(), steps);

        pipelineRegistry.refreshCache();
        log.info("[Vibe] YAML 导入流水线: {}({}), 阶段 {} 个", code, name, steps.size());
        return new ImportResult(template.getId(), code, steps.size());
    }

    // ==================== 解析与物化 ====================

    /** extends 基线序列物化;无 extends 时读 stages 纯列表 */
    private List<SeqEntry> baseSequence(Map<String, Object> root, Map<Long, PipelineStage> stageById) {
        Object extendsCode = root.get("extends");
        if (extendsCode != null && !String.valueOf(extendsCode).isBlank()) {
            PipelineTemplate base = pipelineTemplateRepository.findByCode(String.valueOf(extendsCode))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "extends 基线流水线不存在: " + extendsCode));
            return pipelineStageStepRepository.findByPipelineIdOrderBySeq(base.getId()).stream()
                    .map(step -> {
                        PipelineStage stage = stageById.get(step.getStageId());
                        if (stage == null) {
                            throw new IllegalArgumentException("基线流水线引用了不存在的阶段: stepId=" + step.getId());
                        }
                        return new SeqEntry(stage.getId(), stage.getStageCode(), step.getStepParams());
                    })
                    .toList();
        }

        List<SeqEntry> entries = new ArrayList<>();
        Object stages = root.get("stages");
        if (stages instanceof List<?> list) {
            for (Object item : list) {
                entries.add(parseStageItem(item, stageById));
            }
        }
        return entries;
    }

    /** stages-: 剔除基线阶段(按 stage_code) */
    private void applyRemovals(Map<String, Object> root, List<SeqEntry> sequence) {
        Object removals = root.get("stages-");
        if (!(removals instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            String code = String.valueOf(item).trim();
            boolean removed = sequence.removeIf(e -> e.stageCode().equals(code));
            if (!removed) {
                throw new IllegalArgumentException("stages- 引用的阶段不在基线序列中: " + code);
            }
        }
    }

    /** stages+: 增阶段(after 指定插在某阶段后,缺省=序列尾) */
    private void applyAdditions(Map<String, Object> root, Map<Long, PipelineStage> stageById,
            List<SeqEntry> sequence) {
        Object additions = root.get("stages+");
        if (!(additions instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            SeqEntry entry = parseStageItem(item, stageById);
            Object after = item instanceof Map<?, ?> map ? map.get("after") : null;
            if (after == null || String.valueOf(after).isBlank()) {
                sequence.add(entry);
                continue;
            }
            String afterCode = String.valueOf(after).trim();
            int index = -1;
            for (int i = 0; i < sequence.size(); i++) {
                if (sequence.get(i).stageCode().equals(afterCode)) {
                    index = i;
                    break;
                }
            }
            if (index < 0) {
                throw new IllegalArgumentException("stages+ 的 after 引用的阶段不在序列中: " + afterCode);
            }
            sequence.add(index + 1, entry);
        }
    }

    private SeqEntry parseStageItem(Object item, Map<Long, PipelineStage> stageById) {
        if (!(item instanceof Map<?, ?> map) || map.get("code") == null) {
            throw new IllegalArgumentException("stages 条目须为 {code: xxx} 形式: " + item);
        }
        String code = String.valueOf(map.get("code")).trim();
        PipelineStage stage = stageById.values().stream()
                .filter(s -> s.getStageCode().equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("阶段不存在(先在阶段库创建): " + code));
        String params = null;
        if (map.get("params") instanceof Map<?, ?> paramsMap && !paramsMap.isEmpty()) {
            try {
                // Jackson 序列化保持值类型(数字不落成字符串,与导出格式对称)
                params = MAPPER.writeValueAsString(paramsMap);
            } catch (Exception e) {
                throw new IllegalArgumentException("params 序列化失败: " + e.getMessage());
            }
        }
        return new SeqEntry(stage.getId(), code, params);
    }

    /** keywords = 基线 keywords + 本段追加(逗号合并去重) */
    private String mergeKeywords(Map<String, Object> root) {
        String own = str(root.get("keywords"));
        Object extendsCode = root.get("extends");
        if (extendsCode == null || String.valueOf(extendsCode).isBlank() || own == null) {
            return own;
        }
        String base = pipelineTemplateRepository.findByCode(String.valueOf(extendsCode))
                .map(PipelineTemplate::getKeywords)
                .orElse(null);
        if (base == null || base.isBlank()) {
            return own;
        }
        Map<String, Boolean> merged = new LinkedHashMap<>();
        for (String kw : base.split("[，,、\\s]+")) {
            if (!kw.isBlank()) {
                merged.put(kw.trim(), true);
            }
        }
        for (String kw : own.split("[，,、\\s]+")) {
            if (!kw.isBlank()) {
                merged.put(kw.trim(), true);
            }
        }
        return String.join(",", merged.keySet());
    }

    private Map<String, Object> parseYaml(String yamlText) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> root = new Yaml().loadAs(yamlText, Map.class);
            if (root == null || root.isEmpty()) {
                throw new IllegalArgumentException("YAML 内容为空");
            }
            return root;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("YAML 解析失败: " + e.getMessage());
        }
    }

    private Map<Long, PipelineStage> stageMap() {
        Map<Long, PipelineStage> map = new LinkedHashMap<>();
        for (PipelineStage stage : pipelineStageRepository.findAllActive()) {
            map.put(stage.getId(), stage);
        }
        return map;
    }

    private String requireText(Map<String, Object> root, String key) {
        Object value = root.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException("YAML 缺少必填字段: " + key);
        }
        return String.valueOf(value).trim();
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    /** 物化过程中的阶段序列条目 */
    private record SeqEntry(Long stageId, String stageCode, String params) {
    }
}

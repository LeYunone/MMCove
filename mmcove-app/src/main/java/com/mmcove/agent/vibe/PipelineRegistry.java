package com.mmcove.agent.vibe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmcove.agent.common.model.entity.PipelineStage;
import com.mmcove.agent.common.model.entity.PipelineStageStep;
import com.mmcove.agent.common.model.entity.PipelineTemplate;
import com.mmcove.agent.common.model.entity.ProductLinePipeline;
import com.mmcove.agent.infra.persistence.repository.PipelineStageRepository;
import com.mmcove.agent.infra.persistence.repository.PipelineStageStepRepository;
import com.mmcove.agent.infra.persistence.repository.PipelineTemplateRepository;
import com.mmcove.agent.infra.persistence.repository.ProductLinePipelineRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 流水线配置注册中心:启动加载 + 管理端写后刷新(仿 {@code ResponseTemplateRegistry})。
 *
 * <p>缓存理由:composeVibePrompt 是外部 AI 每任务必触发的高频 MCP 调用,
 * 一次组装需"模板+全部步骤+全部阶段+产品线覆盖"四份数据,
 * 而这些表是几十行的低频变更配置——缓存收益远大于一致性成本。
 *
 * <p>步骤级覆盖(step_params)在装载时物化进 {@link EffectiveStage};
 * 产品线覆盖(stage_overrides)缓存原始绑定,由 compose 按线应用(避免按线展开爆炸)。
 *
 * @since 2026-09-11
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PipelineRegistry {

    private final PipelineStageRepository pipelineStageRepository;
    private final PipelineTemplateRepository pipelineTemplateRepository;
    private final PipelineStageStepRepository pipelineStageStepRepository;
    private final ProductLinePipelineRepository productLinePipelineRepository;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** intent_type → 全局共享流水线(含物化后的步骤序列) */
    private volatile Map<String, EffectivePipeline> intentShared = Map.of();

    /** product_line_id → (intent_type → 该线私有副本) */
    private volatile Map<Long, Map<String, EffectivePipeline>> intentPrivateByLine = Map.of();

    /** pipeline_code → 生效流水线 */
    private volatile Map<String, EffectivePipeline> byCode = Map.of();

    /** pipeline_id → 生效流水线(run 执行实例反查用) */
    private volatile Map<Long, EffectivePipeline> byId = Map.of();

    /** product_line_id → (pipeline_id → 绑定覆盖) */
    private volatile Map<Long, Map<Long, ProductLinePipeline>> overridesByLine = Map.of();

    /** 启用的全部阶段(阶段库目录/管理端) */
    private volatile List<PipelineStage> allStages = List.of();

    @PostConstruct
    public void init() {
        refreshCache();
    }

    /** 从数据库全量重载(管理端写后/手动刷新调用) */
    public synchronized void refreshCache() {
        List<PipelineTemplate> templates = pipelineTemplateRepository.findAll().stream()
                .filter(t -> t.getStatus() != null && t.getStatus() == 1)
                .toList();
        Map<Long, PipelineStage> stageById = pipelineStageRepository.findAllActive().stream()
                .collect(Collectors.toMap(PipelineStage::getId, Function.identity(), (a, b) -> a));
        List<PipelineStageStep> steps = pipelineStageStepRepository.findByPipelineIds(
                templates.stream().map(PipelineTemplate::getId).toList());

        Map<String, EffectivePipeline> intentMap = new HashMap<>();
        Map<Long, Map<String, EffectivePipeline>> privateMap = new HashMap<>();
        Map<String, EffectivePipeline> codeMap = new HashMap<>();
        Map<Long, EffectivePipeline> idMap = new HashMap<>();
        for (PipelineTemplate template : templates) {
            List<EffectiveStage> stages = steps.stream()
                    .filter(s -> s.getPipelineId().equals(template.getId()))
                    .sorted((a, b) -> Integer.compare(a.getSeq(), b.getSeq()))
                    .map(step -> buildEffectiveStage(stageById.get(step.getStageId()), step))
                    .filter(s -> s != null)
                    .toList();
            if (stages.isEmpty()) {
                log.warn("流水线 [{}] 无可用阶段(阶段被停用或未配置序列),跳过缓存", template.getPipelineCode());
                continue;
            }
            EffectivePipeline pipeline = new EffectivePipeline(template, stages);
            if (template.getProductLineId() == null) {
                intentMap.put(template.getIntentType(), pipeline);
            } else {
                privateMap.computeIfAbsent(template.getProductLineId(), k -> new HashMap<>())
                        .put(template.getIntentType(), pipeline);
            }
            codeMap.put(template.getPipelineCode(), pipeline);
            idMap.put(template.getId(), pipeline);
        }

        Map<Long, Map<Long, ProductLinePipeline>> lineMap = new HashMap<>();
        for (ProductLinePipeline binding : productLinePipelineRepository.findAll().stream()
                .filter(b -> b.getEnabled() != null && b.getEnabled() == 1).toList()) {
            lineMap.computeIfAbsent(binding.getProductLineId(), k -> new HashMap<>())
                    .put(binding.getPipelineId(), binding);
        }

        this.intentShared = Map.copyOf(intentMap);
        this.intentPrivateByLine = Map.copyOf(privateMap);
        this.byCode = Map.copyOf(codeMap);
        this.byId = Map.copyOf(idMap);
        this.overridesByLine = Map.copyOf(lineMap);
        this.allStages = List.copyOf(new ArrayList<>(stageById.values()));

        int privCount = privateMap.values().stream().mapToInt(Map::size).sum();
        log.info("[Vibe] 已加载 流水线 共享{}条+私有{}条 / 阶段{}个 / 产品线绑定{}条",
                intentMap.size(), privCount, stageById.size(), lineMap.size());
    }

    /** 装载物化:阶段默认值 ← 步骤级覆盖(step_params) */
    private EffectiveStage buildEffectiveStage(PipelineStage stage, PipelineStageStep step) {
        if (stage == null) {
            log.warn("步骤引用的阶段不存在或已停用: stepId={}, stageId={}", step.getId(), step.getStageId());
            return null;
        }
        int subAgentCount = stage.getSubAgentCount() != null ? stage.getSubAgentCount() : 1;
        String extraPrompt = null;
        if (step.getStepParams() != null && !step.getStepParams().isBlank()) {
            try {
                JsonNode params = MAPPER.readTree(step.getStepParams());
                if (params.hasNonNull("subAgentCount") && params.get("subAgentCount").asInt() > 0) {
                    subAgentCount = params.get("subAgentCount").asInt();
                }
                if (params.hasNonNull("extraPrompt")) {
                    extraPrompt = params.get("extraPrompt").asText();
                }
            } catch (Exception e) {
                log.warn("步骤参数解析失败(忽略覆盖,用阶段默认): stepId={}, params={}",
                        step.getId(), step.getStepParams());
            }
        }
        return new EffectiveStage(stage.getId(), stage.getStageCode(), stage.getName(),
                stage.getRolePrompt(), subAgentCount, stage.getDeliverable(), stage.getGateRule(), extraPrompt);
    }

    /** 按意图取全局共享流水线(分类候选/无默认线场景) */
    public Optional<EffectivePipeline> byIntent(String intentType) {
        return Optional.ofNullable(intentShared.get(intentType));
    }

    /**
     * 按意图+产品线选流水线(产品线隔离核心):本线私有副本优先,回退全局共享模板。
     */
    public Optional<EffectivePipeline> byIntentForLine(String intentType, Long productLineId) {
        if (productLineId != null) {
            EffectivePipeline priv = intentPrivateByLine
                    .getOrDefault(productLineId, Map.of()).get(intentType);
            if (priv != null) {
                return Optional.of(priv);
            }
        }
        return Optional.ofNullable(intentShared.get(intentType));
    }

    public Optional<EffectivePipeline> byCode(String pipelineCode) {
        return Optional.ofNullable(byCode.get(pipelineCode));
    }

    /** 按流水线 id 取生效流水线(执行实例反查,不受意图改配影响) */
    public Optional<EffectivePipeline> byId(Long pipelineId) {
        return Optional.ofNullable(byId.get(pipelineId));
    }

    /** 活跃意图类型集合(LLM 分类候选与结果校验用) */
    public List<String> activeIntentTypes() {
        return List.copyOf(intentShared.keySet());
    }

    /** 全部全局共享流水线(意图分类候选用;私有副本是共享意图的本线定制,不参与分类) */
    public List<EffectivePipeline> allPipelines() {
        return List.copyOf(intentShared.values());
    }

    /** 某产品线可见的流水线(本线私有 + 全局共享;getPipelineStage 工具/管理端列表用) */
    public List<EffectivePipeline> pipelinesForLine(Long productLineId) {
        Map<String, EffectivePipeline> merged = new LinkedHashMap<>(intentShared);
        if (productLineId != null) {
            merged.putAll(intentPrivateByLine.getOrDefault(productLineId, Map.of()));
        }
        return List.copyOf(merged.values());
    }

    public List<PipelineStage> allStages() {
        return allStages;
    }

    /** 产品线对某流水线的绑定覆盖(可空=未定制,用全局默认) */
    public Optional<ProductLinePipeline> overrideOf(Long productLineId, Long pipelineId) {
        if (productLineId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(overridesByLine
                .getOrDefault(productLineId, Map.of())
                .get(pipelineId));
    }

    /**
     * 生效阶段(阶段库默认值 + 步骤级覆盖已物化)。
     */
    public record EffectiveStage(Long stageId, String stageCode, String name, String rolePrompt,
            int subAgentCount, String deliverable, String gateRule, String extraPrompt) {
    }

    /**
     * 生效流水线(模板 + 按 seq 升序的阶段序列)。
     */
    public record EffectivePipeline(PipelineTemplate template, List<EffectiveStage> stages) {
    }
}
